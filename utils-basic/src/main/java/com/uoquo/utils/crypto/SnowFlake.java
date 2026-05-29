/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.crypto;

import java.util.Random;

/**
 * 描述：雪花算法. <br>
 * 原理：将long的64 bit分开定义<br>
 * 改进：应用码8 bit，节点码4 bit（0作为系统预留，所以最多255个应用，每个应用15个节点）
 * <ul>
 *   <li><b>原 始：</b>1位预留-41位时间戳-5位中心码-5位机器码-12位序号；可用69年， 单节点每毫秒支持4096个并发.</li>
 *   <li><b>改造1：</b>1位预留-41位时间戳-8位应用码-4位节点码-10位序号；可用069年，单节点每毫秒支持1024个并发.</li>
 *   <li><b>改造2：</b>1位预留-42位时间戳-8位应用码-4位节点码-9位序号； 可用139年，单节点每毫秒支持512个并发（<b>目前使用</b>）.</li>
 *   <li><b>改造3：</b>1位预留-43位时间戳-8位应用码-4位节点码-8位序号； 可用278年，单节点每毫秒支持256个并发.</li>
 *   <li><b>改造4：</b>1位预留-44位时间戳-8位应用码-4位节点码-7位序号； 可用557年，单节点每毫秒支持128个并发.</li>
 * </ul>
 * 日期：2019-05-27 09:03 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2019-05-27     Administrator.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class SnowFlake {

    /**
     * 起始的时间戳.
     */
    public static final long START_STMP = 1577808000000L; // 2020-01-01 00:00:00

    /**
     * 每一部分占用的位数.
     */
    private static final int SEQUENCE_BIT = 9; // 序列号占位（0x1FF）
    private static final int APP_CODE_BIT = 8; // 应用码占位（0xFF）
    private static final int APP_NODE_BIT = 4; // 节点码占位（0xF）

    /**
     * 每一部分的最大值.
     */
    private static final int MAX_APP_CODE_NUM = ~(-1 << APP_CODE_BIT); // 255
    private static final int MAX_APP_NODE_NUM = ~(-1 << APP_NODE_BIT); // 15
    private static final int MAX_SEQUENCE     = ~(-1 << SEQUENCE_BIT); // 1023

    /**
     * 每一部分向左的位移.
     */
    private static final int APP_NODE_LEFT = SEQUENCE_BIT;
    private static final int APP_CODE_LEFT = APP_NODE_LEFT + APP_NODE_BIT;
    private static final int TIMESTMP_LEFT = APP_CODE_LEFT + APP_CODE_BIT;

    private long lastStmp = -1L; // 上一次时间戳
    private int appCode   = 0;   // 应用码
    private int appNode   = 0;   // 节点码
    private int sequence  = 0;   // 序列号
    private int seqBegin  = 0;   // 序列起始值
    private final Random random = new Random();

    /**
     * 构造函数.
     * @param code 应用编码
     * @param node 应用节点
     * @param random 起始编号是否随机
     */
    public SnowFlake(String code, String node, boolean random) {
        this(Integer.parseInt(code, 16), Integer.parseInt(node, 16), random);
    }

    /**
     * 构造函数.
     * @param code 应用编码
     * @param node 应用节点
     * @param random 起始编号是否随机（在并发不大时，防止每次生成的都是偶数）
     */
    public SnowFlake(int code, int node, boolean random) {
        this.appCode = code & MAX_APP_CODE_NUM;
        this.appNode = node & MAX_APP_NODE_NUM;
        if (this.appCode != code) {
            throw new IllegalArgumentException(String.format("应用编码必须是[0, 0x%s]以内的值",
                    Integer.toString(code & MAX_APP_CODE_NUM, 16).toUpperCase()));
        } else if (this.appNode != node) {
            throw new IllegalArgumentException(String.format("应用节点必须是[0, 0x%s]以内的值",
                    Integer.toString(code & MAX_APP_NODE_NUM, 16).toUpperCase()));
        }
        if (random) {
            this.seqBegin = MAX_SEQUENCE / 3; // 从三分之一的区间随机开始
        }
    }

    /**
     * 产生下一个ID（雪花算法）.
     */
    public synchronized long nextId() {
        long currStmp = getThisMillis();
        if (currStmp < lastStmp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate id");
        }
        
        if (currStmp == lastStmp) {
            // 相同毫秒内，序列号自增
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // 同一毫秒的序列数已经达到最大
            if (sequence == 0) {
                currStmp = getNextMillis();
            }
        } else {
            // 设置不同毫秒的起始值
            sequence = (seqBegin == 0) ? 0 : random.nextInt(seqBegin);
        }
        
        lastStmp = currStmp;
        
        return (currStmp - START_STMP) << TIMESTMP_LEFT // 时间戳
                | (long) appCode << APP_CODE_LEFT       // 应用
                | (long) appNode << APP_NODE_LEFT       // 节点
                | sequence;                             // 序列号
    }

    
    /**
     * 获取下一毫秒的数值.
     */
    private long getNextMillis() {
        long mill = getThisMillis();
        while (mill <= lastStmp) {
            mill = getThisMillis();
        }
        return mill;
    }
    
    /**
     * 获取当前毫秒的数值.
     */
    private long getThisMillis() {
        return System.currentTimeMillis();
    }
}
