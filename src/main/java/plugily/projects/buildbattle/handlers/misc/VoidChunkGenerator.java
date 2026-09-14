package plugily.projects.buildbattle.handlers.misc;

import java.util.Collections;
import java.util.List;

import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

// Void generator so chunks outside a copied map stay void, as Splegg-OG.
public class VoidChunkGenerator extends ChunkGenerator {

    @Override
    public boolean shouldGenerateNoise() {

        return false;

    }

    @Override
    public boolean shouldGenerateSurface() {

        return false;

    }

    @Override
    public boolean shouldGenerateCaves() {

        return false;

    }

    @Override
    public boolean shouldGenerateDecorations() {

        return false;

    }

    @Override
    public boolean shouldGenerateMobs() {

        return false;

    }

    @Override
    public boolean shouldGenerateStructures() {

        return false;

    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {

        return Collections.emptyList();

    }

}
