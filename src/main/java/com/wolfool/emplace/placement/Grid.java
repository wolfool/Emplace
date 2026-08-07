package com.wolfool.emplace.placement;

import net.momirealms.craftengine.core.entity.furniture.AlignmentRule;
import net.momirealms.craftengine.core.util.Pair;
import org.jetbrains.annotations.Nullable;

/**
 * 가구가 앉는 자리를 격자에 맞춥니다.
 *
 * <p><b>기본은 {@link #HALF} — 0.5칸 격자입니다.</b> 가구가 놓이는 좌표가
 * 언제나 0.5 의 배수가 되므로, 블록 한가운데에도 블록 경계에도 딱 맞출 수
 * 있습니다. 반 칸 옮겨 붙이는 것이 되니 탁자 두 개를 이어 붙이거나 의자를
 * 탁자 모서리에 대는 것이 그냥 됩니다. F 를 누르면 이것과 가구 기본을 오갑니다.
 *
 * <p>CraftEngine 은 가구마다 정렬 규칙을 갖고 있지만(가운데·4분할) 그 규칙이
 * 늘 맞지는 않습니다. {@link #FURNITURE} 는 그 규칙을 그대로 쓰는 자리라
 * 예전과 똑같이 동작합니다.
 *
 * <p>{@link #CENTER} 는 무엇을 찍든 블록 한가운데로 보냅니다 — 0.5칸 격자와
 * 달리 경계에는 못 놓습니다. 나머지는 더 잘게 맞출 때 쓰는 것으로,
 * {@code /emplace 격자 <이름>} 으로 골라 씁니다.
 */
public enum Grid {

    /** 가구 정의에 적힌 규칙을 그대로 씁니다. */
    FURNITURE("가구 기본", -1),
    /** 블록 한가운데 (정수 좌표 + 0.5). 무엇을 찍든 언제나 한가운데입니다. */
    CENTER("한가운데", 1),
    /** <b>0.5칸 격자. 기본값입니다.</b> 좌표가 …0.0 · 0.5 · 1.0… 에만 놓입니다. */
    HALF("½칸", 2),
    /** 0.25칸 격자. 의자를 탁자 네 귀퉁이에 붙일 때. */
    QUARTER("¼칸", 4),
    /** 0.125칸 격자. 잘게 맞출 때. */
    EIGHTH("⅛칸", 8),
    /** 안 맞춥니다. 본 자리에 그대로 놓입니다. */
    FREE("자유", 0);

    private final String korean;
    /** 한 블록을 몇 칸으로 나눌지. -1 은 가구 규칙, 0 은 안 나눔. */
    private final int divisions;

    Grid(String korean, int divisions) {
        this.korean = korean;
        this.divisions = divisions;
    }

    public String korean() {
        return korean;
    }

    public int divisions() {
        return divisions;
    }

    /**
     * 블록 안 어디를 찍었는지(0~1)를 이 격자에 맞춰 옮깁니다.
     *
     * @param alignment 가구가 갖고 있는 규칙. {@link #FURNITURE} 일 때만 씁니다
     */
    public Pair<Double, Double> apply(double fx, double fz, AlignmentRule alignment) {
        return switch (this) {
            case FURNITURE -> {
                try {
                    yield alignment.apply(Pair.of(fx, fz));
                } catch (Throwable t) {
                    // 규칙이 이상하면 한가운데로 떨어뜨립니다.
                    // 놓는 것 자체가 막히는 것보다 낫습니다
                    yield Pair.of(0.5, 0.5);
                }
            }
            case FREE -> Pair.of(clamp(fx), clamp(fz));
            case CENTER -> Pair.of(0.5, 0.5);
            default -> Pair.of(snap(fx, divisions), snap(fz, divisions));
        };
    }

    /**
     * 블록 안 위치(0~1)를 <b>1/n 칸 간격의 격자선</b>에 맞춥니다.
     *
     * <p>½칸이면 0 · 0.5 · 1 이 나옵니다. 블록 좌표에 더해지므로 결국 가구가
     * 놓이는 세계 좌표가 <b>언제나 0.5 의 배수</b>가 됩니다 — 어느 블록에서
     * 놓든 같은 선 위에 서므로 여러 개를 놓아도 줄이 딱 맞습니다.
     *
     * <p><b>1.0 이 나오는 것은 정상입니다.</b> 그 값은 옆 블록의 0.0 과 같은
     * 자리, 곧 두 블록의 경계선입니다. 예전에는 이걸 '블록 밖' 으로 보고 칸
     * 한가운데(0.25 · 0.75)로 보냈는데, 그러면 격자 간격은 0.5 인데 놓이는
     * 자리는 0.25 씩 어긋난 곳이라 <b>블록 경계에 딱 맞출 수가 없었습니다.</b>
     * 탁자 두 개를 나란히 붙이는 것이 안 됐던 이유가 이것입니다.
     */
    private static double snap(double v, int n) {
        return Math.round(clamp(v) * n) / (double) n;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /**
     * F 로 눌렀을 때 넘어갈 다음 격자.
     *
     * <p><b>여섯을 다 돌지 않고 둘만 오갑니다</b> — 지금 고른 것과 가구 기본.
     * 놓을 때 실제로 필요한 것은 "격자에 맞춰라 / 말아라" 하나이고, 그걸
     * 쓰려고 여섯 단계를 돌리게 하면 도리어 번거롭습니다.
     *
     * <p>기본값이 0.5칸이므로 아무것도 안 고른 사람은 <b>0.5칸 ↔ 가구 기본</b>을
     * 오갑니다. {@code /emplace 격자 <이름>} 으로 다른 것을 골라 두면 그것과
     * 가구 기본을 오갑니다.
     */
    public Grid next() {
        return this == FURNITURE ? HALF : FURNITURE;
    }

    /** 이름으로 찾습니다. 한국어 이름과 영문 키를 다 받습니다. */
    public static @Nullable Grid byName(@Nullable String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.trim();
        for (Grid grid : values()) {
            if (grid.korean.equals(trimmed)) return grid;
            if (grid.name().equalsIgnoreCase(trimmed)) return grid;
        }
        return null;
    }
}
