package com.agentplatform.common.text;

import java.util.regex.Pattern;

/**
 * 个人信息掩码工具（共享内核）。
 *
 * <p>用于反馈列表、洞察展示等出口：手机号保留前三后四、身份证保留前六后四、
 * 银行卡保留前四后四。方法无状态、可重复调用；掩码后的文本不会再次命中规则。</p>
 */
public final class PiiMasker {
    /** 中国大陆手机号；前后不能紧跟数字，避免误伤更长数字串。 */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    /** 18 位身份证：6 位地址码 + 8 位出生日期 + 4 位校验码。 */
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{6})\\d{8}(\\d{3}[0-9Xx])(?!\\d)");
    /** 15 位老身份证：6 位地址码 + 6 位出生日期 + 3 位顺序码。 */
    private static final Pattern ID_CARD_15 = Pattern.compile("(?<!\\d)(\\d{6})\\d{6}(\\d{3})(?!\\d)");
    /** 16–19 位银行卡号。 */
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)(\\d{16,19})(?!\\d)");

    private PiiMasker() {
    }

    /**
     * 对文本中的手机号、身份证、银行卡做掩码。
     *
     * @param text 原始文本，可为 null
     * @return 掩码后的文本；null 返回 null
     */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) return text;
        String masked = ID_CARD.matcher(text)
                .replaceAll(match -> match.group(1) + "********" + match.group(2));
        masked = ID_CARD_15.matcher(masked)
                .replaceAll(match -> match.group(1) + "******" + match.group(2));
        masked = BANK_CARD.matcher(masked)
                .replaceAll(match -> maskBankCard(match.group()));
        return PHONE.matcher(masked)
                .replaceAll(match -> maskPhone(match.group()));
    }

    /**
     * 手机号保留前三后四。
     *
     * @param phone 11 位手机号
     * @return 掩码文本
     */
    private static String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 银行卡保留前四后四。
     *
     * @param card 16–19 位卡号
     * @return 掩码文本
     */
    private static String maskBankCard(String card) {
        return card.substring(0, 4) + "****" + card.substring(card.length() - 4);
    }
}
