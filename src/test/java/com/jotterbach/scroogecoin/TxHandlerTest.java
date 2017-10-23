package com.jotterbach.scroogecoin;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.math.BigInteger;
import java.security.*;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TxHandlerTest {
     /*
        Based on the Github gist https://gist.github.com/mentlsve/ef15013f1e6e5abd82996b34a7b4131b
     */

    private static PrivateKey private_key_scrooge;
    private static PublicKey public_key_scrooge;

    private static PrivateKey private_key_alice;
    private static PublicKey public_key_alice;

    private static PrivateKey private_key_bob;
    private static PublicKey public_key_bob;

    private final Transaction rootTx = new Transaction();


    private void signAndFinalizeTransaction(Transaction tx, PrivateKey privateKey) throws InvalidKeyException, SignatureException, NoSuchAlgorithmException {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(tx.getRawDataToSign(0));
        byte[] sig = signature.sign();
        tx.addSignature(sig, 0);
        tx.finalize();
    }

    @Before
    public void setupKeys() throws NoSuchAlgorithmException, NoSuchProviderException {
        Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
        keyGen.initialize(1024, random);

        // Generating two key pairs, one for Scrooge and one for Alice
        KeyPair pair = keyGen.generateKeyPair();
        private_key_scrooge = pair.getPrivate();
        public_key_scrooge = pair.getPublic();

        pair = keyGen.generateKeyPair();
        private_key_alice = pair.getPrivate();
        public_key_alice = pair.getPublic();

        pair = keyGen.generateKeyPair();
        private_key_bob = pair.getPrivate();
        public_key_bob = pair.getPublic();
    }

    @Before
    public void setup_root_tx() throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {
        // START - ROOT TRANSACTION
        // Generating a root transaction tx out of thin air, so that Scrooge owns a coin of value 10
        // By thin air I mean that this tx will not be validated, I just need it to get a proper Transaction.Output
        // which I then can put in the UTXOPool, which will be passed to the TXHandler
        rootTx.addOutput(10, public_key_scrooge);

        // that value has no meaning, but tx.getRawDataToSign(0) will access in.prevTxHash;
        byte[] initialHash = BigInteger.valueOf(1695609641).toByteArray();
        rootTx.addInput(initialHash, 0);

        signAndFinalizeTransaction(rootTx, private_key_scrooge);
        // END - ROOT TRANSACTION
    }


    @Test
    public void testValidScroogeCoinModel() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        // The transaction output of the root transaction is unspent output
        UTXOPool utxoPool = new UTXOPool();
        UTXO utxo = new UTXO(rootTx.getHash(),0);
        utxoPool.addUTXO(utxo, rootTx.getOutput(0));

        // START - PROPER TRANSACTION
        Transaction tx2 = new Transaction();

        // the Transaction.Output of tx at position 0 has a value of 10
        tx2.addInput(rootTx.getHash(), 0);

        // I split the coin of value 10 into 3 coins and send all of them for simplicity to the same address (Alice)
        tx2.addOutput(5, public_key_alice);
        tx2.addOutput(3, public_key_alice);
        tx2.addOutput(2, public_key_alice);

        // There is only one (at position 0) Transaction.Input in tx2
        // and it contains the coin from Scrooge, therefore I have to sign with the private key from Scrooge
        signAndFinalizeTransaction(tx2, private_key_scrooge);

        // remember that the utxoPool contains a single unspent Transaction.Output which is the coin from Scrooge
        TxHandler txHandler = new TxHandler(utxoPool);
        Assert.assertTrue(txHandler.isValidTx(tx2));
        Assert.assertEquals(1, txHandler.handleTxs(new Transaction[]{tx2}).length);
    }

    @Test
    public void testValidScroogeWithDuplicatedTx() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        // The transaction output of the root transaction is unspent output
        UTXOPool utxoPool = new UTXOPool();
        UTXO utxo = new UTXO(rootTx.getHash(),0);
        utxoPool.addUTXO(utxo, rootTx.getOutput(0));

        // START - PROPER TRANSACTION
        Transaction tx2 = new Transaction();

        // the Transaction.Output of tx at position 0 has a value of 10
        tx2.addInput(rootTx.getHash(), 0);

        // I split the coin of value 10 into 3 coins and send all of them for simplicity to the same address (Alice)
        tx2.addOutput(5, public_key_alice);
        tx2.addOutput(3, public_key_alice);
        tx2.addOutput(2, public_key_alice);

        // There is only one (at position 0) Transaction.Input in tx2
        // and it contains the coin from Scrooge, therefore I have to sign with the private key from Scrooge
        signAndFinalizeTransaction(tx2, private_key_scrooge);

        // START - PROPER TRANSACTION
        Transaction tx3 = new Transaction();
        tx3.addInput(rootTx.getHash(), 0);
        // I split the coin of value 10 into 3 coins and send all of them for simplicity to the same address (Alice)
        tx3.addOutput(5, public_key_bob);
        tx3.addOutput(4, public_key_bob);
        tx3.addOutput(1, public_key_scrooge);

        // There is only one (at position 0) Transaction.Input in tx2
        // and it contains the coin from Scrooge, therefore I have to sign with the private key from Scrooge
        signAndFinalizeTransaction(tx3, private_key_scrooge);

        // remember that the utxoPool contains a single unspent Transaction.Output which is the coin from Scrooge
        TxHandler txHandler = new TxHandler(utxoPool);
        Assert.assertTrue(txHandler.isValidTx(tx2));
        Assert.assertTrue(txHandler.isValidTx(tx3));
        Assert.assertEquals(tx2.numInputs(), tx2.numInputs());
        Assert.assertEquals(tx2.numOutputs(), tx2.numOutputs());

        // Ensure txHandler removes double spending tx
        Assert.assertEquals(1, txHandler.handleTxs(new Transaction[]{tx2, tx3}).length);

    }

}
