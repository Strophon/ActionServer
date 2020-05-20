package com.github.strophon.util;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;

import com.google.common.io.BaseEncoding;

public class Randomizer {
    private static SecureRandom SR;

    static {
        try {
            SR = SecureRandom.getInstance("NativePRNGNonBlocking");
        } catch(NoSuchAlgorithmException e) {
            SR = new SecureRandom(); // ideally this doesn't happen; we don't want blocking behavior
        }
    }

    private SecureRandom sr;

    public Randomizer(byte[] seed) {
        setSeed(seed);
    }

    private void setSeed(byte[] seed) {
        try {
            sr = SecureRandom.getInstance("SHA1PRNG");
        } catch(NoSuchAlgorithmException e) {
            throw new RuntimeException(e); // this shouldn't happen
        }
        sr.setSeed(seed);
    }

    public float upToOne() {
        return sr.nextFloat();
    }

    public float upToOneHalf() {
        return sr.nextFloat() / 2;
    }

    public float upToOneThird() {
        return sr.nextFloat() / 3;
    }

    public float upToOneQuarter() {
        return sr.nextFloat() / 4;
    }

    public float upToOneFifth() {
        return sr.nextFloat() / 5;
    }

    public float upToOneSixth() {
        return sr.nextFloat() / 6;
    }

    public float upToOneEighth() {
        return sr.nextFloat() / 8;
    }

    public float upToOneTenth() {
        return sr.nextFloat() / 10;
    }

    public float upToOneTwentieth() {
        return sr.nextFloat() / 20;
    }

    public boolean nextBoolean() {
        return sr.nextBoolean();
    }

    public int nextInt() {
        return sr.nextInt();
    }

    public int nextInt(int x) {
        return sr.nextInt(x);
    }

    public long nextLong() {
        return sr.nextLong();
    }

    public long nextLong(long x) {
        if(x <= 0) {
            throw new IllegalArgumentException("Zero or negative bound given to nextLong()");
        }
        long value = sr.nextLong() % x;
        return value < 0 ? value + x : value;
    }

    public void shuffle(List<?> list) {
        Collections.shuffle(list, sr);
    }

    public static String getFreshToken() {
        return BaseEncoding.base32().encode(getFreshTokenBytes());
    }

    public static String getFreshToken(int size) {
        return BaseEncoding.base32().encode(getFreshTokenBytes(size));
    }

    public static byte[] getFreshTokenBytes() {
        return getFreshTokenBytes(20);
    }

    public static byte[] getFreshTokenBytes(int bytes) {
        return SR.generateSeed(bytes);
    }
}
