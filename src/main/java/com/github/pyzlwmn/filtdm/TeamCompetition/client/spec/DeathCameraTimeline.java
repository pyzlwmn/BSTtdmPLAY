package com.github.pyzlwmn.filtdm.TeamCompetition.client.spec;

import net.ptcrys.fpsmatch.common.camera.SequenceClock;

public final class DeathCameraTimeline {

    public static final int HIT_TICKS = 5;

    public static final int FALL_START_TICKS = 3;

    public static final int FALL_END_TICKS = 14;

    public static final int FADE_START_TICKS = 14;

    public static final int PRESENTATION_TICKS = 40;

    public static final int TOTAL_TICKS = 100;

    private final SequenceClock clock = new SequenceClock();
    private boolean active;

    public SequenceClock clock() {
        return clock;
    }

    public void start() {
        clock.reset();
        active = true;
    }

    public void reset() {
        clock.reset();
        active = false;
    }

    public void tick() {
        if (active) clock.tick();
    }

    public boolean active() {
        return active;
    }

    public boolean inPresentation() {
        return active && clock.ticks() < PRESENTATION_TICKS;
    }

    public boolean holding() {
        return active && clock.ticks() >= PRESENTATION_TICKS;
    }

    public boolean finished() {
        return active && clock.ticks() >= TOTAL_TICKS;
    }

    public float elapsed(float partialTick) {
        return active ? clock.ticks() + Math.max(0, Math.min(1, partialTick)) : 0.0F;
    }

    public float shakeProgress(float partialTick) {
        if (!active) return 0;
        float time = elapsed(partialTick);
        return time < HIT_TICKS ? 1.0F - smooth(time / HIT_TICKS) : 0.0F;
    }

    public float fallProgress(float partialTick) {
        if (!active) return 0;
        float time = elapsed(partialTick);
        return smooth((time - FALL_START_TICKS) / (FALL_END_TICKS - FALL_START_TICKS));
    }

    public float fadeProgress(float partialTick) {
        if (!active) return 0;
        float time = elapsed(partialTick);
        return smooth((time - FADE_START_TICKS) / (PRESENTATION_TICKS - FADE_START_TICKS));
    }

    private static float smooth(float value) {
        value = Math.max(0.0F, Math.min(1.0F, value));
        return value * value * (3.0F - 2.0F * value);
    }
}
