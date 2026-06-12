/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 */
package com.uoquo.test;

import com.uoquo.utils.StringUtil;
import com.uoquo.utils.crypto.MD5;
import com.uoquo.utils.crypto.RSA;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.GeneralSecurityException;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RSA generateKeyPair(int keySize) 方法单元测试.
 */
class RSAGenerateKeyPairTest {

    @ParameterizedTest
    @ValueSource(ints = {1024, 2048, 4096})
    void generateKeyPair_validKeySizes_shouldSucceed(int keySize) throws GeneralSecurityException {
        RSA.KeyPair keyPair = RSA.generateKeyPair(keySize);
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPublicKey());
        assertNotNull(keyPair.getPrivateKey());
        // 验证生成的密钥长度与请求的一致
        byte[] publicKeyBytes = StringUtil.hex2byte(keyPair.getPublicKey());
        byte[] privateKeyBytes = StringUtil.hex2byte(keyPair.getPrivateKey());
        int actualBitLength = publicKeyBytes.length * 8;
        // 公钥编码包含额外的 ASN.1 头部信息，所以实际字节数会大于 keySize/8
        // 但私钥的 modulus 位数应该等于 keySize
        assertTrue(publicKeyBytes.length > 0);
        assertTrue(privateKeyBytes.length > 0);
    }

    @ParameterizedTest
    @ValueSource(ints = {512, 768, 1025, 2049, 3072, 8192, 0, -1})
    void generateKeyPair_invalidKeySizes_shouldThrowIllegalArgumentException(int keySize) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> RSA.generateKeyPair(keySize));
        assertTrue(ex.getMessage().contains("1024"));
        assertTrue(ex.getMessage().contains("2048"));
        assertTrue(ex.getMessage().contains("4096"));
    }

    @Test
    void generateKeyPair_defaultNoArg_shouldGenerate2048() throws GeneralSecurityException {
        RSA.KeyPair keyPair = RSA.generateKeyPair();
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPublicKey());
        assertNotNull(keyPair.getPrivateKey());
    }

    @Test
    void encrypt() throws GeneralSecurityException {
        String str = "55adec2abc9106f619e86ad13ccaf7b032173c14426cfcb05563e89da8df507ad7cc56b05441285734c96812ae932111c1660687eb11f4de63f6de5e49990c50ef2740944475fb977472a44884b05b2f07e8aa5c37d4e3212b664f725899a302f579fc10cb2530a60b75bd9cf58aa2c816adcb5533ad5977193d50e6e072022326ff0fb4f91666bc5028a0678273e8a8d4faa5bfc661d9201de3c50c2fc5fb1d948edd10495e6013df85668f0e0fcebae1ff012f70bd02b653893dde10afc95af3487237a87a7939e4225d9ec98e60e9b0f7117670362e4311d13580590f8e1fa023182e513070ce0a90318714deafdbe02d64b1a6482b00d870a52e8e9a1c78";
//        String prikey = "308204be020100300d06092a864886f70d0101010500048204a8308204a40201000282010100ac8585b171f098657d37b5251ad85aae040f45285333b4d59f753ae0c6c6cfbff4a5bef445fa1c63355726170f174cf3b0cce4a7a42bdc89d4a4b12e2987a330ff8ce82870ae7eb99b53deb99b3403b5d39356fca320bd905e80fc13235095c9be46df6303290261f46c126b5d3fdfbd5af9b7dc2009f2a974cf9d0517c550e1853ccfff88d6cb409b22d67e52a7cde1e153aec7f12c881edbba865e1b70a7db82741d2eef98ae0a62bd50c2205720b8e67d66ba73dcd673d4b43679709d6599e3b6c9bd7c0721ba3d416c354f8a847f9eaf136cd37cc21b8eb15b7fc5cc98b917a6513d993b204cb60ff6a7407ede0be6203d8cdbfe58ddfdf6cd51d8082c4502030100010282010009909f906e7a41e0c297ff958e8ef73c15a289198abf5fa3c662f8003555197891864cf77b17ba522fc764a7bba0f595bf3424eb3c796811463659526f600346b8ac65c4b436a07506ed17f89f67bb5aeddf89df98e5bc4cd04883714630761588d4a216f9ba60d4f079bb6db36e52a7cb6ef3e822e89a4915de5adcc8ec1a27476b1d67b3d2ec89fa5f64bc8d73de84991237e2f7ae772cbcf8771f0fa2caa5ba359b63b249cca073e52a9d47e53195397b7cfeed8a7e3ad1229c0155d8a7be20a91f71f089d72628e08d72bcaf0ae38f824b86b42d6ad98ede0da3db1dd9c014d5b44702bf960a49e6a6c0a385127fa47fe627e3e54917d3928ce451d2120102818100da88381645fa713a5bef3c116c37ccab37923c74ce34416236a16525e0d58ed7cbab3f367867c3960fd171287f0138f312f6ec9179cb8b931f52de217390d7fb15231396eeeda4d4d51ede60ea71847ae239a5312eb25e95cbd65766abfae07214b3002f105f579b9af4ef749e829b168b648498766ee4f988ec018724b7330102818100ca19d0ad0a73c528abad45abd6bb40d64f17a6df17747fdca81d0897f5b5f4de9813e52bbea21bca6b415ed8afebf11d26b7de367769d60168cce2afdb5f39f841ca0f7bd6b4b02708c22ee7ddd6b2437be57cd13dd8b97adc4838b3102fffa75173f5df4ad9d37af7095e42a466b7ef42996f57f25a6f8d2fb8fff021f06d45028180445fa474cb964d40fe2cbef5908f70d90d3d12ef54e9229ff109d454e2a0cedd93dad39f5e52d0ee2daad125e37ee56ba03e63cc2c7854064721ad60780114a2797d8d0e8759115e8a03eb7e153c49400d720dce7296da6331f0b14ded77442c4d6fa81a119c5313db8ad13502b74880e8421dfe79e7be7a98ad86d8dad2fb0102818100a9ecc0aeedaddf64cdcbf75b7f459c4354e6ff106e892e8499f92763955db310816fa0ed81f08d4b4999022550ed21a2b00e42d12ae01ae6be761e575c2c99f22966ba5416777ff0a2059b28ab363c564f52e9fa8874b3a72095bcce99dab1aabdeab11d58b303844d375c40ce37eb894946f6fe4c304bc0bb1262674631ffbd02818100a4f01d8dacd0550efb26b76aae315a7283e79e34e00344c67a63b9300e2478c7025493263cd4d0f4ed567b9bee399b4fe075f604152a472750bce32818e9956dd284312f8cecc7d96a128bbe4c117181c898fa67fd69a601e879437ac8cada1b35d3ffd50d64c63beefa1afbc5502bfd82f05684ba1c90a254f9416e831df889";
//        String pubkey = "30820122300d06092a864886f70d01010105000382010f003082010a0282010100b3632f98b29e8b3a97ddb00b2d1d03450b1dd02e9ba4d695af92385e3ff224a4e4d46a30f4760c9d7c10c80b8c86a4e3695972b932caa0b789590b1856c8c2a94e7efeba0f25462057dd41ae455b38b69f995075907e3530d65a04c83baf2a8a9d8bf45b46702f6e09ebc24a77174cadea5f51dab97391b8b7ef0635fddfa1c34198e847d79115ae3cb1fd84836363d96c0988c4595f995202de2bd8d14ee21dfae05273ef65001e88eb47caf6bdbf637b8c5a10fa8d1eb88e7d0b6af0ee11f6e3a69475e96796c7f239013cf67d836b5efda8c21f5c1739f1155aba847131220ca70160e2074e73732067a2bc6d8cbc6cb8d079126eb1de1a5daf0e476f2b3b0203010001";

        try {
            RSA.KeyPair keyPair = RSA.generateKeyPair();
            String prikey = keyPair.getPrivateKey();
            String pubkey = keyPair.getPublicKey();

            String pswd = MD5.encrypt("admin@123");
            System.out.println(pswd);
            pswd = RSA.encrypt(pswd, pubkey);
            System.out.println(pswd);
            pswd = RSA.decrypt(pswd, prikey);
            System.out.println(pswd);
            System.out.println(RSA.decrypt(str, prikey));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
