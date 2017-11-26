package com.jotterbach.blockchain;

import com.jotterbach.blockchain.Crypto;
import com.jotterbach.blockchain.Transaction;
import com.jotterbach.blockchain.UTXO;
import com.jotterbach.blockchain.UTXOPool;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TxHandler {

    private UTXOPool utxoPool;
    private UTXOPool claimedUtxos = new UTXOPool();
    private List<Transaction.Input> claimedInputs = new ArrayList<>();
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool,
     * (2) the signatures on each input of {@code tx} are valid,
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        // IMPLEMENT THIS

        List<UTXO> currentUtxos = tx.getInputs().stream()
                // Ensure that the current inputs are still part of the UTXO pool.
                // To do this we need to identify their hash of origin and the corresponding output index
                .map(input -> new UTXO(input.prevTxHash, input.outputIndex))
                .collect(Collectors.toList());

        return allcurrentUtxosInUtxoPool(currentUtxos) &&
                allSignaturesValid(tx) &&
                noMultiplyClaimedUtxo(currentUtxos) &&
                allOutputValuesNonNegative(tx) &&
                sumInputLargerEqualThanSumOutput(tx);
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // IMPLEMENT THIS

        List<Transaction> validTxn = new ArrayList<>();
        // Atomicity is the key. Streaming makes this hard! We need to ensure that a validated transaction is added
        // to the output Transactions and that the corresponding UTXO is removed at the same time so that a new TX will
        // not correctly validate!
        for (Transaction tx : possibleTxs) {
            if (isValidTx(tx)) {
                validTxn.add(tx);
                tx.getInputs().forEach(this::removeUtxo);
                addNewUtxos(tx);
            }
        }
        return validTxn.toArray(new Transaction[validTxn.size()]);
    }


    private void removeUtxo (Transaction.Input input) {
        // Ensure to remove correct UTXO from the pool!
        UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
        this.utxoPool.removeUTXO(utxo);
    }

    private void addNewUtxos(Transaction tx) {
        for (int i = 0; i < tx.numOutputs(); i++){
            UTXO utxo = new UTXO(tx.getHash(), i);
            this.utxoPool.addUTXO(utxo, tx.getOutput(i));
        }
    }

    private boolean allcurrentUtxosInUtxoPool(List<UTXO> currentUtxos) {
        return currentUtxos.stream().allMatch(utxo -> this.utxoPool.contains(utxo));
    }

    private boolean allSignaturesValid(Transaction tx) {
        for (int i = 0; i < tx.numInputs(); i++){
            Transaction.Input input = tx.getInput(i);
            UTXO utxo = new UTXO(input.prevTxHash, input.outputIndex);
            Transaction.Output previousTxOutput = this.utxoPool.getTxOutput(utxo);
            if (previousTxOutput == null) {
                return false;
            }
            if (!Crypto.verifySignature(previousTxOutput.address,
                    tx.getRawDataToSign(i),
                    input.signature)) {
                return false;
            }

        }
        return true;
    }

    private boolean noMultiplyClaimedUtxo(List<UTXO> currentUtxos) {
        return currentUtxos.size() == currentUtxos.stream().distinct().count();
    }

    private boolean allOutputValuesNonNegative(Transaction tx) {
        return tx.getOutputs().stream().allMatch(output -> output.value >= 0.0D);
    }

    private boolean sumInputLargerEqualThanSumOutput(Transaction tx) {
        double currentTXOutputSum = tx.getOutputs().stream().mapToDouble(output -> output.value).sum();

        double inputTransactionSum = tx.getInputs().stream()
                .map(input -> new UTXO(input.prevTxHash, input.outputIndex))
                .mapToDouble(utxo -> this.utxoPool.getTxOutput(utxo).value)
                .sum();
        return inputTransactionSum >= currentTXOutputSum;
    }

    public UTXOPool getUTXOPool() {
        return utxoPool;
    }
}
