package com.hm.badminton;

import com.hm.badminton.service.agent.tools.EquipmentQueryNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EquipmentQueryNormalizerTest {

    @Test
    void shouldNormalizeConversationalShoeRequest() {
        assertThat(EquipmentQueryNormalizer.normalize("我要买一个羽毛球鞋子")).isEqualTo("鞋");
        assertThat(EquipmentQueryNormalizer.normalize("推荐一双羽毛球运动鞋")).isEqualTo("鞋");
    }

    @Test
    void genericEquipmentRequestShouldNotBecomeSqlKeyword() {
        assertThat(EquipmentQueryNormalizer.normalize("推荐新手装备（羽毛球）")).isNull();
        assertThat(EquipmentQueryNormalizer.normalize("我要羽毛球装备")).isNull();
        assertThat(EquipmentQueryNormalizer.normalize("帮我按预算筛装备（羽毛球）")).isNull();
        assertThat(EquipmentQueryNormalizer.normalize("按价格筛选这些装备")).isNull();
        assertThat(EquipmentQueryNormalizer.normalize("不限预算的羽毛球装备")).isNull();
        assertThat(EquipmentQueryNormalizer.normalize("预算不限，推荐羽毛球装备")).isNull();
        assertThat(EquipmentQueryNormalizer.normalize("不考虑价格的羽毛球装备")).isNull();
        assertThat(EquipmentQueryNormalizer.normalize("查看不限品类装备")).isNull();
    }

    @Test
    void structuredCommandShouldUseValidatedKeywordInsteadOfDisplayText() {
        assertThat(EquipmentQueryNormalizer.resolveQueryKeyword(
                "不限预算的羽毛球装备", null, true)).isNull();
        assertThat(EquipmentQueryNormalizer.resolveQueryKeyword(
                "不限预算的羽毛球鞋", "鞋", true)).isEqualTo("鞋");
    }

    @Test
    void freeTextShouldStillRecognizeCategoryAndReuseMemory() {
        assertThat(EquipmentQueryNormalizer.resolveQueryKeyword(
                "给我推荐羽毛球鞋", null, false)).isEqualTo("鞋");
        assertThat(EquipmentQueryNormalizer.resolveQueryKeyword(
                "500元以内", "鞋", false)).isEqualTo("鞋");
    }

    @Test
    void shouldKeepUsefulBrandKeyword() {
        assertThat(EquipmentQueryNormalizer.normalize("帮我找李宁装备")).isEqualTo("李宁");
    }
}
