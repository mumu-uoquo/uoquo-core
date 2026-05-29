/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 描述：日期工具类. <br>
 * 日期：2018-01-18 13:29 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-18     xuhz.           创建
 * 2.0          2024-01-01                     迁移到java.time API
 * </pre>
 * @since   JDK 1.8
 * @version 2.0
 * @author  uoquo team
 */
public class DateUtil {
    private static final Logger log = LoggerFactory.getLogger(DateUtil.class);

    // ZoneOffset.UTC
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.systemDefault();

    /**
     * 时间戳正则表达式：至少10位数字（秒级时间戳）
     */
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^\\d{10,}$");

    /**
     * 年份正则表达式：4位数字
     */
    private static final Pattern YEAR_PATTERN = Pattern.compile("^\\d{4}$");

    /**
     * 紧凑时间格式正则：14位数字
     */
    private static final Pattern COMPACT_DATETIME_PATTERN = Pattern.compile("^\\d{14}$");

    /**
     * 时间格式：yyyy .
     */
    public static final String FORMAT_YEAR     = "yyyy";

    /**
     * 时间格式：HH:mm .
     */
    public static final String FORMAT_TIME_HM   = "HH:mm";

    /**
     * 时间格式：HH:mm:ss .
     */
    public static final String FORMAT_TIME      = "HH:mm:ss";

    /**
     * 日期格式：yyyy-MM .
     */
    public static final String FORMAT_MONTH     = "yyyy-MM";

    /**
     * 日期格式：yyyy-MM-dd .
     */
    public static final String FORMAT_DATE      = "yyyy-MM-dd";

    /**
     * 日期格式：yyyy-MM-dd HH:mm .
     */
    public static final String FORMAT_DATE_HM   = "yyyy-MM-dd HH:mm";

    /**
     * 日期格式：yyyy-MM-dd HH:mm:ss .
     */
    public static final String FORMAT_DATE_TIME = "yyyy-MM-dd HH:mm:ss";

    /**
     * 日期格式：yyyyMMddHHmmss .
     */
    public static final String FORMAT_SECONDS = "yyyyMMddHHmmss";

    /**
     * 日期格式：yyyy-MM-dd HH:mm:ss.SSS .
     */
    public static final String FORMAT_TIMESTAMP = "yyyy-MM-dd HH:mm:ss.SSS";

    /**
     * 日期格式：时间戳 .
     */
    public static final String FORMAT_TIMESTAMP_LONG = "TIMESTAMP";

    /**
     * 日期格式：yyyy-MM-dd'T'HH:mm:ss.SSS'Z'，如2018-01-23T15:33:40.032Z.
     */
    public static final String FORMAT_UTC = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    /**
     * 日期格式：yyyy-MM-dd'T'HH:mm:ss'Z'，如2018-01-23T15:33:40Z.
     */
    public static final String FORMAT_UTC_SECOND = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    /**
     * 日期格式：yyyy-MM-dd'T'HH:mm:ss.SSSZ，如2018-01-23T15:33:40.032+0800.
     */
    public static final String FORMAT_UTC_TIMEZONE = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    // 预定义的DateTimeFormatter实例（线程安全）
    // private static final DateTimeFormatter FORMATTER_YEAR = DateTimeFormatter.ofPattern(FORMAT_YEAR);
    private static final DateTimeFormatter FORMATTER_DATE = DateTimeFormatter.ofPattern(FORMAT_DATE);
    private static final DateTimeFormatter FORMATTER_DATE_TIME = DateTimeFormatter.ofPattern(FORMAT_DATE_TIME);
    private static final DateTimeFormatter FORMATTER_TIMESTAMP = DateTimeFormatter.ofPattern(FORMAT_TIMESTAMP);
    private static final DateTimeFormatter FORMATTER_UTC = DateTimeFormatter.ofPattern(FORMAT_UTC)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter FORMATTER_UTC_SECOND = DateTimeFormatter.ofPattern(FORMAT_UTC_SECOND)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter FORMATTER_UTC_TIMEZONE = DateTimeFormatter.ofPattern(FORMAT_UTC_TIMEZONE);

    /**
     * 转换日期为字符串.
     * @param date 日期
     */
    public static String toString(Date date) {
        return toString(date, 0, FORMAT_UTC);
    }

    /**
     * 转换日期为字符串.
     * @param date             原始日期（非null）
     * @param pattern          日期格式（如"yyyy-MM-dd HH:mm:ss"）
     */
    public static String toString(Date date, String pattern) {
        return toString(date, 0, pattern);
    }

    /**
     * 转换日期为字符串.
     * @param date             原始日期（非null）
     * @param num              偏移天数（正数加，负数减，0不偏移）
     * @param pattern          日期格式（如"yyyy-MM-dd HH:mm:ss"）
     */
    public static String toString(Date date, int num, String pattern) {
        // 1. 空值处理
        if (date == null) {
            return null;
        }
        // 2. 时间戳格式直接返回
        if (StringUtil.isNull(pattern) || FORMAT_TIMESTAMP_LONG.equals(pattern)) {
            return String.valueOf(date.getTime());
        }
        try {
            // 3. Date -> Instant（UTC时间戳），并偏移num天（基于UTC，避免时区干扰）
            Instant instant = date.toInstant();
            if (num != 0) {
                instant = instant.plus(num, ChronoUnit.DAYS);
            }
            // 4. 初始化格式化器（指定Locale.ENGLISH，与原代码保持一致）
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
            // 5. 处理UTC时区（pattern以'Z'结尾，如"yyyy-MM-dd'T'HH:mm:ss'Z'"）
            if (pattern.endsWith("'Z'")) {
                // 绑定UTC时区并格式化（ZonedDateTime确保时区正确）
                return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC).format(formatter);
            } else {
                // 默认时区（系统默认时区）：Instant -> LocalDateTime（通过系统时区转换）
                // 与原SimpleDateFormat默认行为一致（依赖系统时区）
                LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
                return localDateTime.format(formatter);
            }
        } catch (Exception e) {
            log.warn("日期格式化出错，时间戳[{}]，偏移天数[{}]，格式[{}].", date.getTime(), num, pattern, e);
            return null;
        }
    }

    /**
     * 转换日期为字符串. (时区转换)
     * @param date             原始日期（非null）
     * @param pattern          日期格式（如"yyyy-MM-dd HH:mm:ss"）
     * @param tzOffsetMinute   时区偏移量（分钟，范围[-1080, 1080]，null表示使用默认时区）
     * @return 格式化后的字符串，异常时返回null
     */
    public static String toString(Date date, String pattern, Integer tzOffsetMinute) {
        return toString(date, 0, pattern, tzOffsetMinute);
    }

    /**
     * 将Date偏移指定天数后，转换为指定时区偏移量和格式的字符串
     *
     * @param date             原始日期（非null）
     * @param num              偏移天数（正数加，负数减，0不偏移）
     * @param pattern          日期格式（如"yyyy-MM-dd HH:mm:ss"）
     * @param tzOffsetMinute   时区偏移量（分钟，范围[-1080, 1080]，null表示使用默认时区）
     * @return 格式化后的字符串，异常时返回null
     */
    public static String toString(Date date, int num, String pattern, Integer tzOffsetMinute) {
        // 1. 校验核心参数
        if (date == null) {
            return null;
        }
        // 2. Date -> Instant（UTC时间戳），偏移num天（基于UTC）
        Instant instant = Instant.ofEpochMilli(date.getTime());
        if (num != 0) {
            instant = instant.plus(num, ChronoUnit.DAYS);
        }
        if (StringUtil.isNull(pattern) || FORMAT_TIMESTAMP_LONG.equals(pattern)) {
            return String.valueOf(instant.toEpochMilli());
        }
        try {
            // 3. 处理时区：根据timeZoneOffset获取ZoneId
            ZoneId zoneId;
            if (tzOffsetMinute != null) {
                // 校验时区偏移量范围（±18小时 = ±1080分钟）
                if (tzOffsetMinute < -1080 || tzOffsetMinute > 1080) {
                    throw new IllegalArgumentException("Time zone offset must be between -1080 and 1080 minutes");
                }
                // 分钟转秒：ZoneOffset.ofTotalSeconds(分钟*60)
                zoneId = ZoneOffset.ofTotalSeconds(tzOffsetMinute * 60);
            } else {
                // 未指定时区时使用系统默认时区
                zoneId = ZoneId.systemDefault();
            }
            // 4. 转换为指定时区的时间并格式化
            ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, zoneId);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
            return zonedDateTime.format(formatter);
        } catch (Exception e) {
            log.warn("日期格式化出错，时间戳[{}]，格式[{}]，时区[{}].", date.getTime(), pattern, tzOffsetMinute, e);
            return null;
        }
    }

    /**
     * 将字符串格式化为日期 .
     * @param text 日期字符串
     * @return 日期
     */
    public static @Nullable Date parse(String text) {
        // 空值处理
        if (StringUtil.isNull(text)) {
            return null;
        }
        // 优先按照时间戳处理
        text = text.trim();
        // 1. 先检查是否是年份格式（4位数字）
        if (YEAR_PATTERN.matcher(text).matches()) {
            return parse(text, FORMAT_YEAR);
        }
        // 2. 检查是否是紧凑日期时间格式（14位数字）
        if (COMPACT_DATETIME_PATTERN.matcher(text).matches()) {
            return parse(text, FORMAT_SECONDS);
        }
        // 3. 尝试解析为时间戳（至少10位数字，且不能是常见日期格式）
        if (isValidTimestamp(text)) {
            try {
                long time = parseTimestamp(text);
                return new Date(time);
            } catch (NumberFormatException e) {
                // 时间戳解析失败，继续其他格式尝试
            }
        }
        // 4. 尝试识别特殊格式
        String format = detectFormat(text);
        if (format != null) {
            try {
                return parse(text, format);
            } catch (DateTimeParseException e) {
                // 格式识别错误，继续尝试其他方式
            }
        }

        // 根据传入数据判断所采用的格式（后续可优化）
        int len = text.length();
        try {
            switch (len) {
                case 28: // "2018-01-23T15:33:40.032+0800".length = 28
                    return parse(text, FORMAT_UTC_TIMEZONE);
                case 24: // "2018-01-23T15:33:40.032Z".length = 24
                    return parse(text, FORMAT_UTC);
                case 20: // "2018-01-23T15:33:40Z".length = 20
                    return parse(text, FORMAT_UTC_SECOND);
                case 23: // "yyyy-MM-dd HH:mm:ss.SSS"
                    return parse(text, FORMAT_TIMESTAMP);
                case 19: // "yyyy-MM-dd HH:mm:ss"
                    return parse(text, FORMAT_DATE_TIME);
                case 16: // "yyyy-MM-dd HH:mm"
                    return parse(text, FORMAT_DATE_HM);
                case 14: // "yyyyMMddHHmmss"
                    return parse(text, FORMAT_SECONDS);
                case 10: // "yyyy-MM-dd"
                    return parse(text, FORMAT_DATE);
                case 7:  // "yyyy-MM"
                    return parse(text, FORMAT_MONTH);
                case 8:  // "HH:mm:ss"
                    return parse(text, FORMAT_TIME);
                case 5:  // "HH:mm"
                    return parse(text, FORMAT_TIME_HM);
                case 4:  // "yyyy"
                    return parse(text, FORMAT_YEAR);
                default:
                    throw new IllegalArgumentException(String.format("不能自动将字符串[ %s ]转换为时间，请自行转换。", text));
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(String.format("不能自动将字符串[ %s ]转换为时间，请自行转换。", text), e);
        }
    }

    /**
     * 日期字符串解析
     */
    public static Date parse(String text, String pattern) {
        // 空值处理
        if (StringUtil.isNull(text)) {
            return null;
        }
        // 优先按照时间戳处理
        text = text.trim();

        try {
            // 根据不同的pattern使用不同的解析策略
            if (FORMAT_UTC.equals(pattern)) {
                Instant instant = Instant.from(FORMATTER_UTC.parse(text));
                return Date.from(instant);
            } else if (FORMAT_UTC_SECOND.equals(pattern)) {
                Instant instant = Instant.from(FORMATTER_UTC_SECOND.parse(text));
                return Date.from(instant);
            } else if (FORMAT_UTC_TIMEZONE.equals(pattern)) {
                ZonedDateTime zonedDateTime = ZonedDateTime.parse(text, FORMATTER_UTC_TIMEZONE);
                return Date.from(zonedDateTime.toInstant());
            } else if (FORMAT_TIMESTAMP.equals(pattern)) {
                LocalDateTime localDateTime = LocalDateTime.parse(text, FORMATTER_TIMESTAMP);
                return toDate(localDateTime.atZone(DEFAULT_ZONE_ID));
            } else if (FORMAT_DATE_TIME.equals(pattern)) {
                LocalDateTime localDateTime = LocalDateTime.parse(text, FORMATTER_DATE_TIME);
                return toDate(localDateTime.atZone(DEFAULT_ZONE_ID));
            } else if (FORMAT_DATE.equals(pattern)) {
                LocalDate localDate = LocalDate.parse(text, FORMATTER_DATE);
                return toDate(localDate.atStartOfDay(DEFAULT_ZONE_ID));
            } else if (FORMAT_YEAR.equals(pattern)) {
                // 年份特殊处理：解析为1月1日
                int year = Integer.parseInt(text);
                LocalDate localDate = LocalDate.of(year, 1, 1);
                return toDate(localDate.atStartOfDay(DEFAULT_ZONE_ID));
            } else {
                // 通用解析
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
                TemporalAccessor temporal = formatter.parse(text);

                // 尝试解析为不同的时间类型
                if (temporal.isSupported(ChronoField.YEAR) && temporal.isSupported(ChronoField.MONTH_OF_YEAR)
                        && temporal.isSupported(ChronoField.DAY_OF_MONTH)) {
                    if (temporal.isSupported(ChronoField.HOUR_OF_DAY)) {
                        // 包含时间信息
                        LocalDateTime localDateTime = LocalDateTime.from(temporal);
                        return toDate(localDateTime.atZone(DEFAULT_ZONE_ID));
                    } else {
                        // 只包含日期信息
                        LocalDate localDate = LocalDate.from(temporal);
                        return toDate(localDate.atStartOfDay(DEFAULT_ZONE_ID));
                    }
                } else if (temporal.isSupported(ChronoField.HOUR_OF_DAY)) {
                    // 只包含时间信息，使用当前日期
                    LocalTime localTime = LocalTime.from(temporal);
                    LocalDateTime localDateTime = LocalDateTime.of(LocalDate.now(), localTime);
                    return toDate(localDateTime.atZone(DEFAULT_ZONE_ID));
                } else {
                    throw new DateTimeParseException("无法解析的时间格式", text, 0);
                }
            }
        } catch (DateTimeParseException e) {
            log.warn("日期解析失败，字符串[{}]，格式[{}]", text, pattern, e);
            throw e;
        }
    }

    /**
     * 判断是否为有效的时间戳
     * 时间戳应该是至少10位的数字，且不能是常见的日期格式
     */
    private static boolean isValidTimestamp(String text) {
        if (!TIMESTAMP_PATTERN.matcher(text).matches()) {
            return false;
        }

        // 排除常见日期格式的误判
        int len = text.length();

        // 4位数字：年份，不是时间戳
        if (len == 4) {
            return false;
        }

        // 14位数字：紧凑日期时间格式 yyyyMMddHHmmss，不是时间戳
        if (len == 14) {
            // 进一步验证：年份应该在合理范围内
            try {
                int year = Integer.parseInt(text.substring(0, 4));
                int month = Integer.parseInt(text.substring(4, 6));
                int day = Integer.parseInt(text.substring(6, 8));
                int hour = Integer.parseInt(text.substring(8, 10));
                int minute = Integer.parseInt(text.substring(10, 12));
                int second = Integer.parseInt(text.substring(12, 14));

                // 验证日期时间是否合理
                if (year >= 1900 && year <= 2100 &&
                        month >= 1 && month <= 12 &&
                        day >= 1 && day <= 31 &&
                        hour >= 0 && hour <= 23 &&
                        minute >= 0 && minute <= 59 &&
                        second >= 0 && second <= 59) {
                    return false; // 这是日期时间格式，不是时间戳
                }
            } catch (Exception e) {
                // 解析失败，可能是时间戳
            }
        }

        // 8位数字：可能是yyyyMMdd，不是时间戳
        if (len == 8) {
            try {
                int year = Integer.parseInt(text.substring(0, 4));
                int month = Integer.parseInt(text.substring(4, 6));
                int day = Integer.parseInt(text.substring(6, 8));

                if (year >= 1900 && year <= 2100 &&
                        month >= 1 && month <= 12 &&
                        day >= 1 && day <= 31) {
                    return false; // 这是日期格式，不是时间戳
                }
            } catch (Exception e) {
                // 解析失败，可能是时间戳
            }
        }

        // 6位数字：可能是HHmmss，不是时间戳
        if (len == 6) {
            try {
                int hour = Integer.parseInt(text.substring(0, 2));
                int minute = Integer.parseInt(text.substring(2, 4));
                int second = Integer.parseInt(text.substring(4, 6));

                if (hour >= 0 && hour <= 23 &&
                        minute >= 0 && minute <= 59 &&
                        second >= 0 && second <= 59) {
                    return false; // 这是时间格式，不是时间戳
                }
            } catch (Exception e) {
                // 解析失败，可能是时间戳
            }
        }

        // 其他长度的纯数字，尝试解析为时间戳
        try {
            long timestamp = Long.parseLong(text);

            // 时间戳的合理范围判断（1970年至今）
            long minTimestamp = 0L; // 1970-01-01
            long maxTimestamp = System.currentTimeMillis() * 2; // 未来一段时间

            // 如果是秒级时间戳（10位），转换为毫秒
            if (text.length() == 10) {
                timestamp *= 1000;
            }

            // 如果是13位数字，已经是毫秒时间戳
            if (text.length() == 13) {
                // 检查是否在合理范围内
                return timestamp >= minTimestamp && timestamp <= maxTimestamp;
            }

            // 其他长度的数字，如果太大可能是时间戳
            return timestamp > 10000000000L; // 大于 2001-09-09
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 解析时间戳字符串
     */
    private static long parseTimestamp(String text) {
        long timestamp = Long.parseLong(text);

        // 根据长度判断时间戳单位
        int len = text.length();

        if (len == 10) {
            // 秒级时间戳，转换为毫秒
            return timestamp * 1000;
        } else if (len == 13) {
            // 毫秒级时间戳
            return timestamp;
        } else if (len > 13) {
            // 微秒或纳秒级时间戳，转换为毫秒
            return timestamp / (long) Math.pow(10, len - 13);
        } else {
            // 小于10位，不是有效时间戳
            throw new NumberFormatException("Invalid timestamp length: " + len);
        }
    }

    /**
     * 检测特殊格式
     */
    private static String detectFormat(String text) {
        // 检查是否包含特殊字符
        if (text.contains("T") && text.contains("Z")) {
            if (text.contains(".")) {
                return text.contains("+") || text.contains("-") ? FORMAT_UTC_TIMEZONE : FORMAT_UTC;
            }
            return FORMAT_UTC_SECOND;
        }

        // 检查是否包含时区信息
        if (text.contains("+") || (text.length() > 5 && text.charAt(text.length() - 5) == '+')) {
            return FORMAT_UTC_TIMEZONE;
        }

        return null;
    }

    // ------------------------------ 日起始/结束时间 ------------------------------
    /**
     * 获取指定日期字符串的起始时间（yyyy-MM-dd 00:00:00）
     */
    public static Date getDayStart(String text) {
        Date date = parse(text);
        return getDayStart(date, 0);
    }

    /**
     * 获取指定日期的起始时间（yyyy-MM-dd 00:00:00）
     */
    public static Date getDayStart(Date date) {
        return getDayStart(date, 0);
    }

    /**
     * 获取指定日期（前/后）num天的起始时间（yyyy-MM-dd 00:00:00）
     */
    public static Date getDayStart(Date date, int num) {
        if (date == null) {
            return null;
        }
        // Date -> LocalDate（系统默认时区），偏移num天，设置为当天起始时间
        LocalDate localDate = date.toInstant()
                .atZone(DEFAULT_ZONE_ID)
                .toLocalDate()
                .plusDays(num);
        return toDate(localDate.atStartOfDay(DEFAULT_ZONE_ID));
    }

    /**
     * 获取指定日期字符串的结束时间（yyyy-MM-dd 23:59:59.499）
     */
    public static Date getDayEnd(String text) {
        Date date = parse(text);
        return getDayEnd(date, 0);
    }

    /**
     * 获取指定日期的结束时间（yyyy-MM-dd 23:59:59.499）
     */
    public static Date getDayEnd(Date date) {
        return getDayEnd(date, 0);
    }

    /**
     * 获取指定日期（前/后）num天的结束时间（yyyy-MM-dd 23:59:59.499）
     * 注：保留原逻辑毫秒数设为499（避免MySQL入库四舍五入）
     */
    public static Date getDayEnd(Date date, int num) {
        if (date == null) {
            return null;
        }
        LocalDate localDate = date.toInstant()
                .atZone(DEFAULT_ZONE_ID)
                .toLocalDate()
                .plusDays(num);
        // 当天23:59:59.499
        LocalDateTime endOfDay = LocalDateTime.of(localDate, LocalTime.of(23, 59, 59, 499_000_000));
        return toDate(endOfDay.atZone(DEFAULT_ZONE_ID));
    }

    // ------------------------------ 周起始/结束时间（周日为起始） ------------------------------
    /**
     * 获取指定日期字符串所在周的起始时间（周日 00:00:00）
     */
    public static Date getWeekStart(String text) {
        Date date = parse(text);
        return getWeekStart(date);
    }

    /**
     * 获取指定日期所在周的起始时间（周日 00:00:00）
     */
    public static Date getWeekStart(Date date) {
        if (date == null) {
            return null;
        }
        // 原逻辑：Calendar.DAY_OF_WEEK=1（周日），java.time默认周起始日为周一，需调整
        LocalDate localDate = date.toInstant()
                .atZone(DEFAULT_ZONE_ID)
                .toLocalDate();
        // 计算周日：若当前是周一（1），减1天；若当前是周日（7），减0天
        LocalDate sunday = localDate.minusDays(localDate.getDayOfWeek().getValue() % 7);
        return toDate(sunday.atStartOfDay(DEFAULT_ZONE_ID));
    }

    /**
     * 获取指定日期字符串所在周的结束时间（周六 23:59:59.499）
     */
    public static Date getWeekEnd(String text) {
        Date date = parse(text);
        return getWeekEnd(date);
    }

    /**
     * 获取指定日期所在周的结束时间（周六 23:59:59.499）
     */
    public static Date getWeekEnd(Date date) {
        if (date == null) {
            return null;
        }
        LocalDate localDate = date.toInstant()
                .atZone(DEFAULT_ZONE_ID)
                .toLocalDate();
        // 周六 = 周日 + 6天
        LocalDate saturday = localDate.minusDays(localDate.getDayOfWeek().getValue() % 7).plusDays(6);
        LocalDateTime endOfSaturday = LocalDateTime.of(saturday, LocalTime.of(23, 59, 59, 499_000_000));
        return toDate(endOfSaturday.atZone(DEFAULT_ZONE_ID));
    }

    // ------------------------------ 月起始/结束时间 ------------------------------
    /**
     * 获取指定日期字符串所在月的起始时间（yyyy-MM-01 00:00:00）
     */
    public static Date getMonthStart(String text) {
        Date date = parse(text);
        return getMonthStart(date);
    }

    /**
     * 获取指定日期所在月的起始时间（yyyy-MM-01 00:00:00）
     */
    public static Date getMonthStart(Date date) {
        if (date == null) {
            return null;
        }
        LocalDate firstDayOfMonth = date.toInstant()
                .atZone(DEFAULT_ZONE_ID)
                .toLocalDate()
                .with(TemporalAdjusters.firstDayOfMonth());
        return toDate(firstDayOfMonth.atStartOfDay(DEFAULT_ZONE_ID));
    }

    /**
     * 获取指定日期字符串所在月的结束时间（yyyy-MM-最后一天 23:59:59.499）
     */
    public static Date getMonthEnd(String text) {
        Date date = parse(text);
        return getMonthEnd(date);
    }

    /**
     * 获取指定日期所在月的结束时间（yyyy-MM-最后一天 23:59:59.499）
     */
    public static Date getMonthEnd(Date date) {
        if (date == null) {
            return null;
        }
        LocalDate lastDayOfMonth = date.toInstant()
                .atZone(DEFAULT_ZONE_ID)
                .toLocalDate()
                .with(TemporalAdjusters.lastDayOfMonth());
        LocalDateTime endOfMonth = LocalDateTime.of(lastDayOfMonth, LocalTime.of(23, 59, 59, 499_000_000));
        return toDate(endOfMonth.atZone(DEFAULT_ZONE_ID));
    }

    // ------------------------------ 年起始/结束时间 ------------------------------
    /**
     * 获取指定日期字符串所在年的起始时间（yyyy-01-01 00:00:00）
     */
    public static Date getYearStart(String text) {
        Date date = parse(text);
        return getYearStart(date);
    }

    /**
     * 获取指定日期所在年的起始时间（yyyy-01-01 00:00:00）
     */
    public static Date getYearStart(Date date) {
        if (date == null) {
            return null;
        }
        LocalDate firstDayOfYear = date.toInstant()
                .atZone(DEFAULT_ZONE_ID)
                .toLocalDate()
                .with(TemporalAdjusters.firstDayOfYear());
        return toDate(firstDayOfYear.atStartOfDay(DEFAULT_ZONE_ID));
    }

    /**
     * 获取指定日期字符串所在年的结束时间（yyyy-12-31 23:59:59.499）
     */
    public static Date getYearEnd(String text) {
        Date date = parse(text);
        return getYearEnd(date);
    }

    /**
     * 获取指定日期所在年的结束时间（yyyy-12-31 23:59:59.499）
     */
    public static Date getYearEnd(Date date) {
        if (date == null) {
            return null;
        }
        LocalDate lastDayOfYear = date.toInstant()
                .atZone(DEFAULT_ZONE_ID)
                .toLocalDate()
                .with(TemporalAdjusters.lastDayOfYear());
        LocalDateTime endOfYear = LocalDateTime.of(lastDayOfYear, LocalTime.of(23, 59, 59, 499_000_000));
        return toDate(endOfYear.atZone(DEFAULT_ZONE_ID));
    }

    // ------------------------------ 新增的java.time方法 ------------------------------

    /**
     * 获取当前时间的LocalDateTime
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(DEFAULT_ZONE_ID);
    }

    /**
     * 获取当前时间的Date对象
     */
    public static Date nowDate() {
        return new Date();
    }

    /**
     * 获取当前时间戳（毫秒）
     */
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 计算两个日期之间的天数差
     */
    public static long daysBetween(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        LocalDate start = startDate.toInstant().atZone(DEFAULT_ZONE_ID).toLocalDate();
        LocalDate end = endDate.toInstant().atZone(DEFAULT_ZONE_ID).toLocalDate();
        return ChronoUnit.DAYS.between(start, end);
    }

    /**
     * 计算两个日期之间的小时差
     */
    public static long hoursBetween(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        LocalDateTime start = startDate.toInstant().atZone(DEFAULT_ZONE_ID).toLocalDateTime();
        LocalDateTime end = endDate.toInstant().atZone(DEFAULT_ZONE_ID).toLocalDateTime();
        return ChronoUnit.HOURS.between(start, end);
    }

    /**
     * 计算两个日期之间的分钟差
     */
    public static long minutesBetween(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        LocalDateTime start = startDate.toInstant().atZone(DEFAULT_ZONE_ID).toLocalDateTime();
        LocalDateTime end = endDate.toInstant().atZone(DEFAULT_ZONE_ID).toLocalDateTime();
        return ChronoUnit.MINUTES.between(start, end);
    }

    /**
     * 判断是否为同一天
     */
    public static boolean isSameDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        LocalDate localDate1 = date1.toInstant().atZone(DEFAULT_ZONE_ID).toLocalDate();
        LocalDate localDate2 = date2.toInstant().atZone(DEFAULT_ZONE_ID).toLocalDate();
        return localDate1.isEqual(localDate2);
    }

    /**
     * 判断是否为闰年
     */
    public static boolean isLeapYear(Date date) {
        if (date == null) {
            return false;
        }
        LocalDate localDate = date.toInstant().atZone(DEFAULT_ZONE_ID).toLocalDate();
        return localDate.isLeapYear();
    }

    /**
     * 获取月份的天数
     */
    public static int getDaysInMonth(Date date) {
        if (date == null) {
            return 0;
        }
        LocalDate localDate = date.toInstant().atZone(DEFAULT_ZONE_ID).toLocalDate();
        return localDate.lengthOfMonth();
    }

    /**
     * 获取年份的天数
     */
    public static int getDaysInYear(Date date) {
        if (date == null) {
            return 0;
        }
        LocalDate localDate = date.toInstant().atZone(DEFAULT_ZONE_ID).toLocalDate();
        return localDate.lengthOfYear();
    }

    /**
     * 获取星期几（1-7，1=周一，7=周日）
     */
    public static int getDayOfWeek(Date date) {
        if (date == null) {
            return 0;
        }
        LocalDate localDate = date.toInstant().atZone(DEFAULT_ZONE_ID).toLocalDate();
        return localDate.getDayOfWeek().getValue();
    }

    /**
     * 获取星期几的中文名称
     */
    public static String getDayOfWeekChinese(Date date) {
        if (date == null) {
            return "";
        }
        int dayOfWeek = getDayOfWeek(date);
        String[] weekDays = {"", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        return weekDays[dayOfWeek];
    }

    // ------------------------------ 工具方法（Date与java.time互转） ------------------------------
    /**
     * 将TemporalAccessor（LocalDateTime/ZonedDateTime等）转换为Date
     */
    private static Date toDate(TemporalAccessor temporal) {
        Instant instant;
        if (temporal instanceof ZonedDateTime) {
            instant = ((ZonedDateTime) temporal).toInstant();
        } else if (temporal instanceof OffsetDateTime) {
            instant = ((OffsetDateTime) temporal).toInstant();
        } else if (temporal instanceof LocalDateTime) {
            // 纯本地日期时间（无时区），使用系统默认时区转换为Instant
            instant = ((LocalDateTime) temporal).atZone(ZoneId.systemDefault()).toInstant();
        } else if (temporal instanceof LocalDate) {
            // 纯日期（无时间），默认取当天00:00:00（系统时区）
            instant = ((LocalDate) temporal).atStartOfDay(ZoneId.systemDefault()).toInstant();
        } else if (temporal instanceof LocalTime) {
            // 纯时间（无日期），默认取当前日期（系统时区）
            instant = LocalDateTime.of(LocalDate.now(), (LocalTime) temporal)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        } else {
            // 其他类型（如Year/Month），暂不支持
            return null;
        }
        return Date.from(instant);
    }

    /**
     * 将Date转换为LocalDateTime
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(DEFAULT_ZONE_ID).toLocalDateTime();
    }

    /**
     * 将Date转换为LocalDate
     */
    public static LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(DEFAULT_ZONE_ID).toLocalDate();
    }

    /**
     * 将Date转换为LocalTime
     */
    public static LocalTime toLocalTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(DEFAULT_ZONE_ID).toLocalTime();
    }

    /**
     * 将LocalDateTime转换为Date
     */
    public static Date toDate(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return Date.from(localDateTime.atZone(DEFAULT_ZONE_ID).toInstant());
    }

    /**
     * 将LocalDate转换为Date
     */
    public static Date toDate(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return Date.from(localDate.atStartOfDay(DEFAULT_ZONE_ID).toInstant());
    }

    /**
     * 将LocalTime转换为Date（使用当前日期）
     */
    public static Date toDate(LocalTime localTime) {
        if (localTime == null) {
            return null;
        }
        return Date.from(LocalDateTime.of(LocalDate.now(), localTime)
                .atZone(DEFAULT_ZONE_ID)
                .toInstant());
    }

    // ------------------------------ 时区相关方法 ------------------------------

    /**
     * 转换时区
     */
    public static Date convertTimeZone(Date date, ZoneId fromZoneId, ZoneId toZoneId) {
        if (date == null) {
            return null;
        }
        ZonedDateTime zonedDateTime = date.toInstant().atZone(fromZoneId);
        ZonedDateTime converted = zonedDateTime.withZoneSameInstant(toZoneId);
        return Date.from(converted.toInstant());
    }

    /**
     * 转换为UTC时间
     */
    public static Date toUTC(Date date) {
        if (date == null) {
            return null;
        }
        return convertTimeZone(date, DEFAULT_ZONE_ID, ZoneOffset.UTC);
    }

    /**
     * 从UTC时间转换为本地时间
     */
    public static Date fromUTC(Date utcDate) {
        if (utcDate == null) {
            return null;
        }
        return convertTimeZone(utcDate, ZoneOffset.UTC, DEFAULT_ZONE_ID);
    }

    /**
     * 获取指定时区的当前时间
     */
    public static Date now(ZoneId zoneId) {
        return Date.from(Instant.now().atZone(zoneId).toInstant());
    }

    // ------------------------------ 日期计算 ------------------------------

    /**
     * 添加天数
     */
    public static Date addDays(Date date, int days) {
        if (date == null) {
            return null;
        }
        LocalDateTime localDateTime = toLocalDateTime(date).plusDays(days);
        return toDate(localDateTime);
    }

    /**
     * 添加小时
     */
    public static Date addHours(Date date, int hours) {
        if (date == null) {
            return null;
        }
        LocalDateTime localDateTime = toLocalDateTime(date).plusHours(hours);
        return toDate(localDateTime);
    }

    /**
     * 添加分钟
     */
    public static Date addMinutes(Date date, int minutes) {
        if (date == null) {
            return null;
        }
        LocalDateTime localDateTime = toLocalDateTime(date).plusMinutes(minutes);
        return toDate(localDateTime);
    }

    /**
     * 添加月份
     */
    public static Date addMonths(Date date, int months) {
        if (date == null) {
            return null;
        }
        LocalDateTime localDateTime = toLocalDateTime(date).plusMonths(months);
        return toDate(localDateTime);
    }

    /**
     * 添加年份
     */
    public static Date addYears(Date date, int years) {
        if (date == null) {
            return null;
        }
        LocalDateTime localDateTime = toLocalDateTime(date).plusYears(years);
        return toDate(localDateTime);
    }

    /**
     * 获取年龄
     */
    public static int getAge(Date birthDate) {
        if (birthDate == null) {
            return 0;
        }
        LocalDate birthLocalDate = toLocalDate(birthDate);
        LocalDate now = LocalDate.now();
        return Period.between(birthLocalDate, now).getYears();
    }

    // ------------------------------ 日期比较 ------------------------------

    /**
     * 判断日期是否在指定范围内
     */
    public static boolean isBetween(Date date, Date startDate, Date endDate) {
        if (date == null || startDate == null || endDate == null) {
            return false;
        }
        return !date.before(startDate) && !date.after(endDate);
    }

    /**
     * 判断日期是否早于指定日期
     */
    public static boolean isBefore(Date date, Date compareDate) {
        if (date == null || compareDate == null) {
            return false;
        }
        return date.before(compareDate);
    }

    /**
     * 判断日期是否晚于指定日期
     */
    public static boolean isAfter(Date date, Date compareDate) {
        if (date == null || compareDate == null) {
            return false;
        }
        return date.after(compareDate);
    }

    /**
     * 获取较早的日期
     */
    public static Date min(Date date1, Date date2) {
        if (date1 == null) {
            return date2;
        }
        if (date2 == null) {
            return date1;
        }
        return date1.before(date2) ? date1 : date2;
    }

    /**
     * 获取较晚的日期
     */
    public static Date max(Date date1, Date date2) {
        if (date1 == null) {
            return date2;
        }
        if (date2 == null) {
            return date1;
        }
        return date1.after(date2) ? date1 : date2;
    }

    // ------------------------------ 格式化相关 ------------------------------

    /**
     * 格式化日期为中文格式（yyyy年MM月dd日）
     */
    public static String formatChinese(Date date) {
        if (date == null) {
            return "";
        }
        LocalDate localDate = toLocalDate(date);
        return localDate.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
    }

    /**
     * 格式化日期为中文格式（yyyy年MM月dd日 HH:mm:ss）
     */
    public static String formatChineseDateTime(Date date) {
        if (date == null) {
            return "";
        }
        LocalDateTime localDateTime = toLocalDateTime(date);
        return localDateTime.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss"));
    }

    /**
     * 获取友好的时间显示（如：刚刚、5分钟前、昨天、3天前等）
     */
    public static String getFriendlyTime(Date date) {
        if (date == null) {
            return "";
        }

        Date now = new Date();
        long diff = now.getTime() - date.getTime();
        long diffSeconds = diff / 1000;
        long diffMinutes = diffSeconds / 60;
        long diffHours = diffMinutes / 60;
        long diffDays = diffHours / 24;

        if (diffSeconds < 60) {
            return "刚刚";
        } else if (diffMinutes < 60) {
            return diffMinutes + "分钟前";
        } else if (diffHours < 24) {
            return diffHours + "小时前";
        } else if (diffDays == 1) {
            return "昨天";
        } else if (diffDays == 2) {
            return "前天";
        } else if (diffDays < 7) {
            return diffDays + "天前";
        } else if (diffDays < 30) {
            return diffDays / 7 + "周前";
        } else if (diffDays < 365) {
            return diffDays / 30 + "个月前";
        } else {
            return diffDays / 365 + "年前";
        }
    }
}

