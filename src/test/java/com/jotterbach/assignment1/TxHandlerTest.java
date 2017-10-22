package com.jotterbach.assignment1;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

//org.apache.commons.codec.digest.DigestUtils.sha256Hex(stringText);

public class TxHandlerTest {

    private Transaction validTx = new Transaction();
    private UTXOPool utxoPool = new UTXOPool();
    @Before
    public void setup() throws NoSuchAlgorithmException{
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] prevTxHash = digest.digest("123456".getBytes(StandardCharsets.UTF_8));

        validTx.addInput(prevTxHash, 0);
        validTx.addInput(digest.digest(prevTxHash), 1);

        UTXO utxo = new UTXO()
    }

    @Test
    public void testValidTxn() {
        TxHandler()
    }
}
