package com.situationpuzzle.service.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.situationpuzzle.config.CookieProperties;
import com.situationpuzzle.service.game.EndingType;
import com.situationpuzzle.service.game.GamePhase;
import com.situationpuzzle.service.game.HintLevel;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GameStateCodecTest {

    private GameStateCodec codec() {
        CookieProperties props = new CookieProperties();
        props.setSecret("test-master-secret-very-long-0123456789");
        return new GameStateCodec(props, new ObjectMapper());
    }

    private ProgressCore sample() {
        return new ProgressCore(
                GamePhase.OPTION_REPLY,
                3,
                103L,
                2,
                4,
                Set.of(1L, 2L),
                Map.of(101L, 40, 102L, 60, 103L, 20),
                Set.of(101L, 102L),
                Set.of(101L, 102L),
                120,
                EndingType.NONE,
                true,
                HintLevel.MID,
                true,
                7,
                new ProgressCore.LastReplyCore(9L, 20, 40, "LLM")
        );
    }

    @Test
    void roundTrip_preservesAllFields() {
        GameStateCodec codec = codec();
        Optional<ProgressCore> decoded = codec.decode(codec.encode(sample()));
        assertThat(decoded).isPresent();
        ProgressCore r = decoded.get();
        assertThat(r.phase()).isEqualTo(GamePhase.OPTION_REPLY);
        assertThat(r.currentStoryOrder()).isEqualTo(3);
        assertThat(r.currentStoryId()).isEqualTo(103L);
        assertThat(r.currentRound()).isEqualTo(2);
        assertThat(r.maxRounds()).isEqualTo(4);
        assertThat(r.selectedOptionIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(r.storyScores()).containsEntry(102L, 60).hasSize(3);
        assertThat(r.completedStoryIds()).containsExactlyInAnyOrder(101L, 102L);
        assertThat(r.truthRevealedStoryIds()).contains(101L);
        assertThat(r.totalScore()).isEqualTo(120);
        assertThat(r.unlockedRealCases()).isTrue();
        assertThat(r.helperHintLevel()).isEqualTo(HintLevel.MID);
        assertThat(r.truthRevealedForCurrentStory()).isTrue();
        assertThat(r.version()).isEqualTo(7);
        assertThat(r.lastReply()).isEqualTo(new ProgressCore.LastReplyCore(9L, 20, 40, "LLM"));
    }

    @Test
    void randomIv_makesCiphertextsDifferButBothDecodeEqual() {
        GameStateCodec codec = codec();
        String a = codec.encode(sample());
        String b = codec.encode(sample());
        // 隨機 IV → 兩次密文不同
        assertThat(a).isNotEqualTo(b);
        // 但都能解回同值
        assertThat(codec.decode(a)).isEqualTo(codec.decode(b));
    }

    @Test
    void tamperedCipherByte_fails() {
        GameStateCodec codec = codec();
        byte[] raw = Base64.getUrlDecoder().decode(codec.encode(sample()));
        raw[raw.length - 1] ^= 0x01; // 翻密文/tag 區最後 1 byte
        String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        assertThat(codec.decode(tampered)).isEmpty();
    }

    @Test
    void tamperedMacByte_fails() {
        GameStateCodec codec = codec();
        byte[] raw = Base64.getUrlDecoder().decode(codec.encode(sample()));
        raw[0] ^= 0x01; // 翻 HMAC 區首 byte
        String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        assertThat(codec.decode(tampered)).isEmpty();
    }

    @Test
    void truncated_fails() {
        GameStateCodec codec = codec();
        String token = codec.encode(sample());
        assertThat(codec.decode(token.substring(0, token.length() / 2))).isEmpty();
    }

    @Test
    void blankAndGarbage_fail() {
        GameStateCodec codec = codec();
        assertThat(codec.decode(null)).isEmpty();
        assertThat(codec.decode("")).isEmpty();
        assertThat(codec.decode("   ")).isEmpty();
        assertThat(codec.decode("not-a-valid-token!!!")).isEmpty();
    }

    @Test
    void blankSecret_stillRoundTripsWithinSameInstance() {
        // 空白 secret → 隨機 master；同實例內仍可 round-trip
        CookieProperties props = new CookieProperties();
        props.setSecret("");
        GameStateCodec codec = new GameStateCodec(props, new ObjectMapper());
        assertThat(codec.decode(codec.encode(sample()))).isPresent();
    }

    @Test
    void fullClearCore_isWellUnder4k() {
        GameStateCodec codec = codec();
        // 全破通關核心：4 則故事分數 + 全部完成/揭謎 + lastReply
        ProgressCore full = new ProgressCore(
                GamePhase.FINISHED, 4, 104L, 4, 4,
                Set.of(1L, 2L, 3L, 4L),
                Map.of(101L, 80, 102L, 80, 103L, 80, 104L, 80),
                Set.of(101L, 102L, 103L, 104L),
                Set.of(101L, 102L, 103L, 104L),
                320, EndingType.TRUE, true, HintLevel.HIGH, true, 99,
                new ProgressCore.LastReplyCore(16L, 20, 80, "LLM")
        );
        String token = codec.encode(full);
        assertThat(token.length()).isLessThan(4096);
        // 遠低於 4KB（實測約數百 byte），保守上限
        assertThat(token.length()).isLessThan(1200);
    }
}
