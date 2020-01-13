package xln.common.utils;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.MurmurHash3;

import java.util.Random;

@Slf4j
public class RandomUtils {

    public static boolean isPowOf2(long x)
    {
        if(x == 0) {
            return false;
        }
        return (x & (x - 1)) == 0;
    }
    //mod must be power of 2
    public static long randomSeq(int offset, int seq, int base, int mod) throws RuntimeException {

        if(!isPowOf2(mod)) {
            throw new RuntimeException("mod must be power of 2");
        }

        long hashSeq = (Math.abs(offset) % (mod - 1)) + seq * ((Math.abs(base) % (mod)) * 2 + 1);
        return hashSeq % mod;
    }
}
