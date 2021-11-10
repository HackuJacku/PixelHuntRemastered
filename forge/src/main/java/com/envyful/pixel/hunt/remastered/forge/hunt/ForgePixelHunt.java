package com.envyful.pixel.hunt.remastered.forge.hunt;

import com.envyful.api.config.util.UtilConfig;
import com.envyful.api.forge.chat.UtilChatColour;
import com.envyful.api.forge.concurrency.UtilForgeConcurrency;
import com.envyful.api.forge.items.ItemBuilder;
import com.envyful.api.forge.server.UtilForgeServer;
import com.envyful.api.gui.factory.GuiFactory;
import com.envyful.api.gui.pane.Pane;
import com.envyful.api.math.UtilRandom;
import com.envyful.api.player.EnvyPlayer;
import com.envyful.api.reforged.pixelmon.PokemonGenerator;
import com.envyful.api.reforged.pixelmon.PokemonSpec;
import com.envyful.api.time.UtilTimeFormat;
import com.envyful.pixel.hunt.remastered.api.PixelHunt;
import com.envyful.pixel.hunt.remastered.forge.PixelHuntForge;
import com.envyful.pixel.hunt.remastered.forge.event.PixelHuntStartEvent;
import com.envyful.pixel.hunt.remastered.forge.event.PixelHuntWonEvent;
import com.google.common.collect.Lists;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.entities.pixelmon.stats.StatsType;
import com.pixelmonmod.pixelmon.enums.EnumSpecies;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ForgePixelHunt implements PixelHunt {

    private final List<String> rewardCommands = Lists.newArrayList();
    private final List<String> rewardDescription = Lists.newArrayList();

    private PokemonGenerator generator;
    private ItemStack displayItem;
    private PokemonSpec currentPokemon;
    private boolean randomCommands;
    private boolean maxIvs;
    private boolean ivMultiplierEnabled;
    private float ivMultiplier;
    private long duration;
    private long currentStart;
    private int guiX;
    private int guiY;

    public ForgePixelHunt(ConfigurationNode node) {
        this.load(node);
    }

    @Override
    public void load(ConfigurationNode config) {
        this.randomCommands = config.node("random-reward-commands").getBoolean(false);
        this.rewardCommands.addAll(UtilConfig.getList(config, String.class, "reward-commands"));
        this.rewardDescription.addAll(UtilConfig.getList(config, String.class, "reward-description"));
        this.maxIvs = config.node("max-ivs").getBoolean();
        this.ivMultiplierEnabled = config.node("iv-multiplier-enabled").getBoolean();
        this.ivMultiplier = config.node("iv-multiplier").getFloat();
        this.duration = TimeUnit.MINUTES.toMillis(config.node("max-duration-minutes").getLong());

        this.guiX = config.node("gui", "X").getInt(0);
        this.guiY = config.node("gui", "Y").getInt(0);

        PokemonGenerator.Builder builder = PokemonGenerator.builder();

        builder.setSpeciesRequired(config.node("require-species").getBoolean())
                .setAllowLegends(config.node("allow-legends").getBoolean())
                .setAllowUltraBeasts(config.node("allow-ultra-beasts").getBoolean())
                .setAllowEvolutions(config.node("allow-evolutions").getBoolean())
                .setGenderRequirement(config.node("require-gender").getBoolean())
                .setGrowthRequirement(config.node("require-growth").getBoolean())
                .setPotentialRequiredGrowths(config.node("number-of-possible-growths").getInt())
                .setNatureRequirement(config.node("require-nature").getBoolean())
                .setPotentialNatureRequirements(config.node("number-of-possible-natures").getInt())
                .setIVRequirement(config.node("require-iv-percentage").getBoolean())
                .setRandomIVGeneration(config.node("random-iv-generation").getBoolean())
                .setOnlyLegends(config.node("legend-only").getBoolean(false));

        if (config.node("require-iv-percentage").getBoolean()) {
            if (config.node("random-iv-generation").getBoolean()) {
                builder.setMinimumIVPercentage(config.node("minimum-iv-percentage").getInt())
                        .setMaximumIVPercentage(config.node("maximum-iv-percentage").getInt());
            } else {
                builder.setMinimumIVPercentage(config.node("required-iv-percentage").getInt())
                        .setMaximumIVPercentage(config.node("required-iv-percentage").getInt());
            }
        }

        for (String type : UtilConfig.getList(config, String.class, "blocked-types")) {
            builder.addBlockedType(EnumSpecies.getFromNameAnyCase(type));
        }

        this.generator = builder.build();
    }

    @Override
    public void display(Pane pane) {
        ItemBuilder builder = new ItemBuilder(this.displayItem);

        for (String s : PixelHuntForge.getInstance().getConfig().getPreLore()) {
            builder.addLore(UtilChatColour.translateColourCodes('&', s.replace("%time%",
                                                                               UtilTimeFormat.getFormattedDuration((this.currentStart + this.duration) - System.currentTimeMillis()))));
        }

        builder.lore(this.currentPokemon.getDescription("§a", "§b"));

        for (String s : PixelHuntForge.getInstance().getConfig().getExtraLore()) {
            builder.addLore(UtilChatColour.translateColourCodes('&', s.replace("%time%",
                    UtilTimeFormat.getFormattedDuration((this.currentStart + this.duration) - System.currentTimeMillis()))));
        }

        builder.addLore(UtilChatColour.translateColourCodes('&', this.rewardDescription).toArray(new String[0]));

        pane.set(this.guiX, this.guiY, GuiFactory.displayableBuilder(ItemStack.class)
                .itemStack(builder.build()).build());
    }

    @Override
    public PokemonSpec generatePokemon() {
        this.currentPokemon = this.generator.generate();
        this.displayItem = this.currentPokemon.getPhoto();
        PixelHuntStartEvent event = new PixelHuntStartEvent(this, this.currentPokemon);

        Pixelmon.EVENT_BUS.post(event);

        this.currentStart = System.currentTimeMillis();
        this.currentPokemon = event.getGeneratedPokemon();

        for (String broadcast : PixelHuntForge.getInstance().getConfig().getSpawnBroadcast()) {
            FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList()
                    .sendMessage(new TextComponentString(UtilChatColour.translateColourCodes('&', broadcast
                            .replace("%pokemon%", this.currentPokemon.getDisplayName()))));
        }

        return this.currentPokemon;
    }

    @Override
    public boolean isBeingHunted(Pokemon pokemon) {
        if (this.currentPokemon == null || pokemon == null) {
            return false;
        }

        return this.currentPokemon.matches(pokemon);
    }

    @Override
    public boolean isSpeciesHunted(Pokemon pokemon) {
        if (this.currentPokemon == null || pokemon == null) {
            return false;
        }

        return this.currentPokemon.doesSpeciesMatch(pokemon);
    }

    @Override
    public void rewardCatch(EnvyPlayer<?> player, Pokemon caught) {
        EntityPlayerMP parent = (EntityPlayerMP) player.getParent();
        PixelHuntWonEvent wonEvent = new PixelHuntWonEvent(this, parent, caught, this.currentPokemon);

        Pixelmon.EVENT_BUS.post(wonEvent);

        if (this.ivMultiplierEnabled) {
            for (StatsType value : StatsType.values()) {
                if (value == StatsType.None || value == StatsType.Accuracy || value == StatsType.Evasion) {
                    continue;
                }

                caught.getIVs().set(value, Math.min(31, (int) (caught.getIVs().get(value) * this.ivMultiplier)));
            }
        } else if (this.maxIvs) {
            for (StatsType value : StatsType.values()) {
                if (value == StatsType.None || value == StatsType.Accuracy || value == StatsType.Evasion) {
                    continue;
                }

                caught.getIVs().set(value, 31);
            }
        }

        UtilForgeConcurrency.runSync(() -> {
            if (this.randomCommands) {
                UtilForgeServer.executeCommand(UtilRandom.getRandomElement(this.rewardCommands).replace("%player%", parent.getName()));
            } else {
                for (String rewardCommand : this.rewardCommands) {
                    UtilForgeServer.executeCommand(rewardCommand.replace("%player%", parent.getName()));
                }
            }
        });
    }

    @Override
    public void end() {
        if (this.currentPokemon == null) {
            return;
        }

        for (String message : PixelHuntForge.getInstance().getConfig().getTimeoutBroadcast()) {
            FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList()
                    .sendMessage(new TextComponentString(UtilChatColour.translateColourCodes('&',
                            message.replace("%pokemon%",
                            this.currentPokemon.getDisplayName()))));
        }
    }

    @Override
    public boolean hasTimedOut() {
        long timePassed = System.currentTimeMillis() - this.currentStart;

        return timePassed >= this.duration;
    }
}
