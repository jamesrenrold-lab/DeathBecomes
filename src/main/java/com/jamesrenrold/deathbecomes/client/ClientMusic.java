package com.jamesrenrold.deathbecomes.client;

import com.jamesrenrold.deathbecomes.DeathBecomes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;

public final class ClientMusic {
    private ClientMusic() { }

    public static void setPlaying(boolean playing) {
        Minecraft minecraft = Minecraft.getInstance();
        if (playing) {
            minecraft.getSoundManager().stop(DeathBecomes.CHASE_MUSIC.get().getLocation(), SoundSource.MUSIC);
            minecraft.getSoundManager().play(SimpleSoundInstance.forMusic(DeathBecomes.CHASE_MUSIC.get()));
        } else {
            minecraft.getSoundManager().stop(DeathBecomes.CHASE_MUSIC.get().getLocation(), SoundSource.MUSIC);
        }
    }
}
