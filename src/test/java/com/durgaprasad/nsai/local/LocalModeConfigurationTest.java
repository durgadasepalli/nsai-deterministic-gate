package com.durgaprasad.nsai.local;

import com.durgaprasad.nsai.local.rules.RuleDefinitionException;
import com.durgaprasad.nsai.local.rules.RuleSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LocalModeConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LocalModeConfiguration.class);

    @Test
    @DisplayName("Local mode is off unless explicitly enabled")
    void inactiveUnlessEnabled() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(LocalRuleConnector.class));
    }

    @Test
    @DisplayName("Enabled: loads the default rule file and registers LOCAL_RULES")
    void registersConnectorWhenEnabled() {
        runner.withPropertyValues("nsai.gateway.local.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(LocalRuleConnector.class);
                    assertThat(ctx.getBean(LocalRuleConnector.class).getSourceIdentifier()).isEqualTo("LOCAL_RULES");
                    assertThat(ctx.getBean(RuleSet.class).forIntent("LOAN_OFFER")).isPresent();
                });
    }

    @Test
    @DisplayName("A missing rule file stops startup")
    void missingRuleFileFailsStartup() {
        runner.withPropertyValues("nsai.gateway.local.enabled=true",
                        "nsai.gateway.local.rules=classpath:does-not-exist.yaml")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause()
                        .isInstanceOf(RuleDefinitionException.class)
                        .hasMessageContaining("Rule file not found"));
    }

    @Test
    @DisplayName("A malformed rule file stops startup with the exact location")
    void malformedRuleFileFailsStartup() {
        runner.withPropertyValues("nsai.gateway.local.enabled=true",
                        "nsai.gateway.local.rules=classpath:rules/typo-rules.yaml")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause()
                        .isInstanceOf(RuleDefinitionException.class)
                        .hasMessageContaining("unknown key 'maxx'"));
    }
}
