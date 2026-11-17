package com.agentplatform.common.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PII 掩码单元测试：覆盖手机号、身份证、银行卡与无敏感信息文本。
 */
class PiiMaskerTest {

    /** 手机号保留前三后四。 */
    @Test
    void masksPhone() {
        assertEquals("请联系 138****5678 处理", PiiMasker.mask("请联系 13812345678 处理"));
    }

    /** 18 位身份证保留前六后四。 */
    @Test
    void masksIdCard() {
        assertEquals("证件 110101********123X", PiiMasker.mask("证件 11010119900101123X"));
    }

    /** 15 位老身份证保留前六后三。 */
    @Test
    void masksLegacyIdCard() {
        assertEquals("证件 110101******123", PiiMasker.mask("证件 110101900101123"));
    }

    /** 银行卡保留前四后四。 */
    @Test
    void masksBankCard() {
        assertEquals("卡号 6222****1234", PiiMasker.mask("卡号 6222021234567891234"));
    }

    /** 普通文本与空值原样返回。 */
    @Test
    void keepsPlainText() {
        assertEquals("退款多久到账", PiiMasker.mask("退款多久到账"));
        assertEquals(null, PiiMasker.mask(null));
        assertEquals("", PiiMasker.mask(""));
    }

    /** 掩码结果再次调用不应继续变化。 */
    @Test
    void maskingIsStable() {
        String once = PiiMasker.mask("手机 13812345678");
        assertEquals(once, PiiMasker.mask(once));
    }
}
