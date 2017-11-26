package com.jotterbach.blockchain;// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;

    private class BlockNode {
        public Block b;
        public BlockNode parent;
        public List<BlockNode> children = new ArrayList<>();
        private int height;
        private int age;

        private UTXOPool utxoPool;

        public BlockNode(Block b, BlockNode parent, UTXOPool utxoPool, int age) {
            this.b = b;
            this.parent = parent;
            this.utxoPool = utxoPool;
            this.age = age;

            // handle genesis block
            if (parent == null) {
                this.height = 1;
            } else {
                this.height = parent.height + 1;
                parent.children.add(this);
            }
        }

        public UTXOPool getUtxoPoolCopy() {
            return new UTXOPool(utxoPool);
        }

        public int getAge() {
            return age;
        }

        public int getHeight() {
            return height;
        }
    }

    private Map<ByteArrayWrapper, BlockNode> blockChain = new HashMap<>();
    private TransactionPool txPool = new TransactionPool();
    private static AtomicInteger age = new AtomicInteger(0);

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        UTXOPool uPool = getUtxoPoolFromCoinbase(genesisBlock);
        BlockNode bn = new BlockNode(genesisBlock, null, uPool, age.incrementAndGet());
        this.blockChain.put(wrapper(genesisBlock.getHash()), bn);
    }

    private BlockNode getMaxHeightNode() {
        Map<Integer, List<BlockNode>> groupedBlockNodes = blockChain.values().stream()
                .collect(Collectors.groupingBy(BlockNode::getHeight, Collectors.toList()));

        int max_height = Collections.max(groupedBlockNodes.keySet());

        List<BlockNode> maxHeightNodes = groupedBlockNodes.get(max_height);
        BlockNode oldestNode = maxHeightNodes.get(0);

        for (BlockNode bn : maxHeightNodes) {
            if (bn.getAge() < oldestNode.getAge()) {
                oldestNode = bn;
            }
        }
        return oldestNode;
    }
    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return getMaxHeightNode().b;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return getMaxHeightNode().utxoPool;
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return txPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        if (block.getPrevBlockHash() == null) {
            return false;
        }
        if (isGenesisBlock(block)) {
            return false;
        }
        if (!isWithinCutoff(block)) {
            return false;
        }
        if (!allTransactionsValid(block)) {
            return false;
        }
        UTXOPool utxoPool = getUtxoPoolFromCoinbase(block);
        BlockNode maxHeightNode = getMaxHeightNode();
        BlockNode bn = new BlockNode(block, maxHeightNode, utxoPool, age.incrementAndGet());
        blockChain.put(wrapper(block.getHash()), bn);
        return true;

    }

    private boolean allTransactionsValid(Block b) {
        BlockNode parentBlock = blockChain.get(wrapper(b.getPrevBlockHash()));
        TxHandler txHandler = new TxHandler(parentBlock.getUtxoPoolCopy());
        Transaction[] allTxn = b.getTransactions().toArray(new Transaction[0]);
        Transaction[] validTxn = txHandler.handleTxs(allTxn);
        return validTxn.length == allTxn.length;
    }

    private boolean isGenesisBlock(Block b) {
        BlockNode parentBlock = blockChain.get(wrapper(b.getPrevBlockHash()));
        return parentBlock == null;
    }

    private boolean isWithinCutoff(Block b) {
        BlockNode parentBlock = blockChain.get(wrapper(b.getPrevBlockHash()));
        return parentBlock.height + 1 > getMaxHeightNode().height - CUT_OFF_AGE;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        txPool.addTransaction(tx);
    }

    private UTXOPool getUtxoPoolFromCoinbase(Block b) {
        UTXOPool uPool = new UTXOPool();
        Transaction coinbase = b.getCoinbase();
        for (int i = 0; i < coinbase.numOutputs(); i++) {
            Transaction.Output output = coinbase.getOutput(i);
            UTXO utxo = new UTXO(coinbase.getHash(), i);
            uPool.addUTXO(utxo, output);
        }
        return uPool;
    }

    private ByteArrayWrapper wrapper(byte[] arr) {
        return new ByteArrayWrapper(arr);
    }
}