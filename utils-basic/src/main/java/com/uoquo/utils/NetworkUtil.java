/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 描述：本地网卡信息. <br>
 * 日期：2019-03-19 14:47 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2019-03-19     Administrator.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class NetworkUtil {
    
    /**
     * 过滤器定义.
     */
    public enum Filter {
        ALL, // 所有网卡
        UP,  // 在线设备
        VIRTUAL,  // 虚拟接口
        LOOPBACK, // 本地环回接口
        PHYSICAL_ONLY; // 物理网卡
        
        /**
         * 判断网卡类型.
         */
        public boolean apply(NetworkInterface input) {
            if (null == input) {
                return false;
            }
            
            try {
                switch (this) {
                    case UP:
                        return input.isUp();
                    case VIRTUAL:
                        return input.isVirtual();
                    case LOOPBACK:
                        return input.isLoopback();
                    case PHYSICAL_ONLY :
                        String displayNames = input.getDisplayName();
                        if ((displayNames != null) && (displayNames.toLowerCase().contains(" virtual "))) {
                            return false;
                        }
                        byte[] hardwareAddress = input.getHardwareAddress();
                        return null != hardwareAddress 
                                && hardwareAddress.length > 0
                                && input.getParent() == null
                                && !input.isVirtual() 
                                && !isVMMac(hardwareAddress);
                    case ALL:
                    default :
                        return true;
                }
            } catch (SocketException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    /**
     * 虚拟网卡定义.
     */
    private static final byte[][] invalidMacs = {
            {0x00, 0x05, 0x69},             // VMWare
            {0x00, 0x1C, 0x14},             // VMWare
            {0x00, 0x0C, 0x29},             // VMWare
            {0x00, 0x50, 0x56},             // VMWare
            {0x08, 0x00, 0x27},             // Virtualbox
            {0x0A, 0x00, 0x27},             // Virtualbox
            {0x00, 0x03, (byte)0xFF},       // Virtual-PC
            {0x00, 0x15, 0x5D}              // Hyper-V
    };
    
    /**
     * 是否虚拟网卡.
     */
    private static boolean isVMMac(byte[] mac) {
        if (null == mac) {
            return false;
        }
        
        for (byte[] invalid: invalidMacs) {
            if (invalid[0] == mac[0] && invalid[1] == mac[1] && invalid[2] == mac[2]) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 根据过滤器{@code filters}指定的条件(AND)返回网卡设备对象.
     * @param filters 过滤器列表（AND关系）
     */
    public static List<NetworkInterface> getNICs(Filter...filters) {
        if (filters.length == 0) {
            filters = new Filter[]{Filter.ALL};
        }
        List<NetworkInterface> result = new ArrayList<NetworkInterface>();
        try {
            Enumeration<NetworkInterface> list = NetworkInterface.getNetworkInterfaces();
            while (list.hasMoreElements()) {
                NetworkInterface nif = list.nextElement();
                boolean flag = true;
                for (Filter filter : filters) {
                    if (!filter.apply(nif)) {
                        flag = false;
                    }
                }
                if (flag) {
                    result.add(nif);
                }
            }
            return result;
        } catch (SocketException e) {
            throw new RuntimeException(e);
        } 
    }
    
    /**
     * 根据{@code IP}前缀返回网卡设备对象.
     * @param ipPre 指定的IP前缀
     */
    public static List<NetworkInterface> getNICs(String ipPre) {
        List<NetworkInterface> result = new ArrayList<NetworkInterface>();
        try {
            Enumeration<NetworkInterface> list = NetworkInterface.getNetworkInterfaces();
            while (list.hasMoreElements()) {
                NetworkInterface nif = list.nextElement();
                Enumeration<InetAddress> adrs = nif.getInetAddresses();
                while (adrs.hasMoreElements()) {
                    String address = adrs.nextElement().getHostAddress();
                    if ((address != null) && address.startsWith(ipPre)) {
                        result.add(nif);
                        break;
                    }
                }
            }
            return result;
        } catch (SocketException e) {
            throw new RuntimeException(e);
        } 
    }
    
    /**
     * 返回所有物理网卡MAC.
     */
    public static List<String> getPhysicalMac() {
        List<NetworkInterface> list = getNICs(Filter.PHYSICAL_ONLY);
        List<String> result = new ArrayList<String>();
        for (NetworkInterface item : list) {
            result.add(getMacAddress(item));
        }
        return result; 
    }
    
    /**
     * 返回指定网卡MAC.
     */
    public static List<String> getMacAddress(Filter...filters) {
        List<NetworkInterface> list = getNICs(filters);
        List<String> result = new ArrayList<String>();
        for (NetworkInterface item : list) {
            result.add(getMacAddress(item));
        }
        return result; 
    }
    
    /**
     * 获取指定IP前缀的网卡mac地址.
     * @param ipPre 指定IP前缀
     */
    public static List<String> getMacAddress(String ipPre) {
        List<NetworkInterface> list = getNICs(ipPre);
        List<String> result = new ArrayList<String>();
        for (NetworkInterface item : list) {
            result.add(getMacAddress(item));
        }
        return result; 
    }
    
    /**
     * 获取指定网卡的mac地址.
     * @param nic 网卡对象
     */
    public static String getMacAddress(NetworkInterface nic) {
        try {
            String mac = formatMac(nic.getHardwareAddress());
            return mac.toUpperCase();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 返回指定网卡的IP4地址.
     * @param nic 网卡信息
     */
    public static String getIp4Address(NetworkInterface nic) {
        Enumeration<InetAddress> adrs = nic.getInetAddresses();
        while (adrs.hasMoreElements()) {
            InetAddress item = adrs.nextElement();
            if (item instanceof Inet4Address) {
                if (StringUtil.notNull(item.getHostAddress())) {
                    //return item.getHostAddress();
                    return formatIp4(item.getAddress());
                }
            }
        }
        return null;
    }
    
    /**
     * 返回指定网卡的IP6地址.
     * @param nic 网卡信息
     */
    public static String getIp6Address(NetworkInterface nic) {
        Enumeration<InetAddress> adrs = nic.getInetAddresses();
        while (adrs.hasMoreElements()) {
            InetAddress item = adrs.nextElement();
            if (item instanceof Inet6Address) {
                if (StringUtil.notNull(item.getHostAddress())) {
                    //return item.getHostAddress();
                    return formatIp6(item.getAddress());
                }
            }
        }
        return null;
    }
    
    /**
     * 格式化MAC地址.
     * 
     * @param source 待转换数据
     */
    private static String formatMac(byte[] source) {
        if (null == source) {
            return "";
        }
        
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < source.length;) {
            byte item = source[i++];
            /*
            String temp = String.copyValueOf(new char[]{
                    Character.forDigit((item & 240) >> 4, 16),
                    Character.forDigit(item  & 15,        16)
            });
            */
            String temp = Integer.toHexString(item & 0xFF);
            if (temp.length() == 1) {
                temp = 0 + temp;
            }
            result.add(temp);
        }
        
        return String.join("-", result);
    }
    
    /**
     * 格式化IP4地址.
     * @param source IP内容
     */
    private static String formatIp4(byte[] source) {
        if (null == source) {
            return "";
        }
        
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < source.length;) {
            byte item = source[i++];
            //String temp = Integer.toUnsignedString(item & 0xFF); // JDK1.6之后
            String temp = Long.toString((long)(item & 0xFF) & 0xffffffffL); // 为了兼容JDK1.6，
            result.add(temp);
        }
        
        return String.join(".", result);
    }
    
    /**
     * 格式化IP6地址.
     * @param source IP内容
     */
    private static String formatIp6(byte[] source) {
        if (null == source) {
            return "";
        }
        
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < source.length; ) {
            byte item1 = source[i++];
            byte item2 = source[i++];
            /*
            String temp = String.copyValueOf(new char[]{
                    Character.forDigit((item1 & 240) >> 4, 16),
                    Character.forDigit(item1  & 15,        16),
                    Character.forDigit((item2 & 240) >> 4, 16),
                    Character.forDigit(item2  & 15,        16)
            });
            */
            String temp1 = Integer.toHexString(item1 & 0xFF);
            String temp2 = Integer.toHexString(item2 & 0xFF);
            if (!"0".equals(temp1) && !"0".equals(temp2)) {
                result.add(temp1 + temp2);
            } else if ("0".equals(temp1)) {
                result.add(temp2);
            } else {
                result.add(temp1 + "00");
            }
        }
        
        return String.join(":", result);
    }
    
}
