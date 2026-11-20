package idle.miau.module;

import idle.miau.module.impl.client.ClickGui;
import idle.miau.module.impl.client.DiscordPresence;
import idle.miau.module.impl.client.IRC;
import idle.miau.module.impl.client.LowGraphics;
import idle.miau.module.impl.client.Notifications;
import idle.miau.module.impl.combat.*;
import idle.miau.module.impl.minigames.*;
import idle.miau.module.impl.movement.*;
import idle.miau.module.impl.movement.MovementFix;
import idle.miau.module.impl.misc.Troll;
import idle.miau.module.impl.misc.Disabler;
import idle.miau.module.impl.other.*;
import idle.miau.module.impl.player.*;
import idle.miau.module.impl.render.Xray;
import idle.miau.module.impl.render.Fullbright;
import idle.miau.module.impl.render.*;
import idle.miau.module.impl.world.*;
import idle.miau.module.impl.bedwar_utils.*;
import idle.miau.utility.font.MiauFontRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    public static List<Module> modules = new ArrayList<>();
    public static List<Module> organizedModules = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, Module> modulesByName = new HashMap<>();
    private static final Map<String, Module> modulesByNormalizedName = new HashMap<>();
    private static final Map<String, Module> modulesByConfigId = new HashMap<>();
    private static final Map<Class<?>, Module> modulesByClass = new HashMap<>();
    private static final Map<String, Module> scriptModulesByName = new HashMap<>();
    private static final Map<String, Module> scriptModulesByNormalizedName = new HashMap<>();

    public static NameHider nameHider;
    public static FastPlace fastPlace;
    public static AutoEnder autoEnder;
    public static MurderMystery murderMystery;
    public static InvMove invmove;
    public static AntiFireball antiFireball;
    public static BedAura bedAura;
    public static BedDefender bedDefender;
    public static Clutch clutch;
    public static FastMine fastMine;
    public static AntiShuffle antiShuffle;
    public static MovementFix movementFix;
    public static Jump45 jump45;
    public static LongJump longJump;
    public static AntiVoid antiVoid;
    public static AntiBot antiBot;
    public static NoSlow noSlow;
    public static AimAssist aimAssist;
    public static KillAura killAura;
    public static FightBot fightBot;
    public static HitSelect hitSelect;
    public static AutoPot autoPot;
    public static Autoblock autoblock;
    public static AutoClicker autoClicker;
    public static HitBox hitBox;
    public static Reach reach;
    public static NoRotate noRotate;
    public static BedESP bedESP;
    public static Blink blink;
    public static Backtrack backtrack;
    public static Chams chams;
    public static ArrayListModule arraylist;
    public static Watermark watermark;
    public static CustomSuffix customSuffix;
    public static Freelook freelook;

    public static PotionHUD potionHUD;
    public static SpotifyMod spotifyMod;
    public static Timer timer;
    public static Fly fly;
    public static SprintReset sprintReset;
    public static Velocity velocity;
    public static KnockbackManipulator knockbackManipulator;
    public static AntiDebuff antiDebuff;
    public static TargetHUD targetHUD;
    public static Emojify emojify;
    public static NoFall noFall;
    public static PlayerESP playerESP;
    public static MobESP mobESP;
    public static KeepSprint keepSprint;
    public static DelayRemover delayRemover;
    public static Piercing piercing;
    public static GhostHand ghostHand;
    public static Fireflies fireflies;
    public static Criticals criticals;
    public static Disabler disabler;
    public static Troll troll;
    public static HideWindow hideWindow;
    public static AntiStaff antiStaff;
    public static Debug debug;
    public static Patcher patcher;
    public static TimerRange timerRange;
    public static LagRange lagRange;
    public static RealLag realLag;

    public static InvManager invManager;
    public static Scaffold scaffold;
    public static Baritone baritone;
    public static ChestStealer chestStealer;
    public static BedWarsTracker bedWarsTracker;
    public static SkyWars skyWars;
    public static BedInventory bedInventory;
    public static AutoWho autoWho;
    public static NoHurtCam noHurtCam;
    public static NoSneakAnimation noSneakAnimation;
    public static NoArmorOverlay noArmorOverlay;
    public static NoBreakParticles noBreakParticles;
    public static AutoTool autoTool;
    public static AutoSwap autoSwap;
    public static Sprint sprint;
    public static Weather weather;
    public static ClickGui clickGui;
    public static Notifications notifications;
    public static BlockIn blockIn;
    public static DiscordPresence discordPresence;
    public static IRC irc;
    public static LowGraphics lowGraphics;
    public static keystrokesmod.script.Manager scriptManager;
    public static AutoWeapon autoWeapon;
    public static Chat chat;
    public static Scoreboard scoreboard;
    public static PearlMacro pearlMacro;

    public void register() {
        this.addModule(clickGui = new ClickGui());
        this.addModule(notifications = new Notifications());
        this.addModule(discordPresence = new DiscordPresence());
        this.addModule(irc = new IRC());
        this.addModule(lowGraphics = new LowGraphics());

        this.addModule(aimAssist = new AimAssist());

        this.addModule(autoClicker = new AutoClicker());
        this.addModule(autoblock = new Autoblock());
        this.addModule(autoWeapon = new AutoWeapon());
        this.addModule(autoPot = new AutoPot());
        this.addModule(blockIn = new BlockIn());
        this.addModule(hitBox = new HitBox());
        this.addModule(killAura = new KillAura());
        this.addModule(fightBot = new FightBot());
        this.addModule(hitSelect = new HitSelect());
        this.addModule(lagRange = new LagRange());
        this.addModule(piercing = new Piercing());
        this.addModule(ghostHand = new GhostHand());
        this.addModule(reach = new Reach());

        this.addModule(new BowAimbot());
        this.addModule(new AutoProjectile());
        this.addModule(new AutoRod());
        this.addModule(new AutoSoup());
        this.addModule(new Refill());
        this.addModule(velocity = new Velocity());
        this.addModule(knockbackManipulator = new KnockbackManipulator());
        this.addModule(sprintReset = new SprintReset());
        this.addLegacyModuleAlias(
                sprintReset,
                "MoreKB",
                "idle.miau.module.impl.combat.SuperKnockback"
        );
        this.addModule(criticals = new Criticals());
        this.addModule(autoWho = new AutoWho());
        this.addModule(bedWarsTracker = new BedWarsTracker());
        this.addModule(skyWars = new SkyWars());
        this.addLegacyModuleAlias(
                bedWarsTracker,
                BedWarsTracker.LEGACY_MODULE_NAME,
                BedWarsTracker.LEGACY_CONFIG_ID,
                BedWarsTracker.INTERIM_CONFIG_ID
        );
        this.addModule(bedInventory = new BedInventory());
        this.addModule(murderMystery = new MurderMystery());
        this.addModule(new SpeedBuilders());
        this.addModule(new SumoFences());
        this.addModule(new AutoRequeue());

        this.addModule(movementFix = new MovementFix());
        this.addModule(jump45 = new Jump45());
        this.addLegacyModuleAlias(jump45, "45 Jump");
        this.addModule(fly = new Fly());
        this.addModule(invmove = new InvMove());
        this.addModule(keepSprint = new KeepSprint());
        this.addModule(longJump = new LongJump());
        this.addModule(antiVoid = new AntiVoid());
        this.addModule(noSlow = new NoSlow());
        this.addModule(new NullMove());
        this.addModule(new Speed());
        this.addModule(sprint = new Sprint());
        this.addModule(new InstantStop());
        this.addModule(new Stasis());
        this.addModule(timer = new Timer());

        this.addModule(baritone = new Baritone());

        this.addModule(new Anticheat());
        this.addModule(antiStaff = new AntiStaff());
        this.addModule(debug = new Debug());
        this.addModule(nameHider = new NameHider());
        this.addModule(timerRange = new TimerRange());
        this.addModule(disabler = new Disabler());
        this.addModule(troll = new Troll());
        this.addModule(hideWindow = new HideWindow());
        this.addModule(patcher = new Patcher());
        this.addModule(new AntiAFK());
        this.addModule(new AutoAuth());
        this.addModule(antiFireball = new AntiFireball());
        this.addModule(autoSwap = new AutoSwap());
        this.addModule(new BridgeAssist());
        this.addModule(clutch = new Clutch());
        this.addModule(scaffold = new Scaffold());
        this.addModule(new Eagle());
        this.addModule(autoTool = new AutoTool());
        this.addModule(bedAura = new BedAura());
        this.addModule(bedDefender = new BedDefender());
        this.addModule(blink = new Blink());
        this.addModule(backtrack = new Backtrack());
        this.addModule(delayRemover = new DelayRemover());
        this.addModule(fastMine = new FastMine());
        this.addModule(fastPlace = new FastPlace());
        this.addModule(autoEnder = new AutoEnder());
        this.addModule(invManager = new InvManager());
        this.addModule(chestStealer = new ChestStealer());
        this.addModule(pearlMacro = new PearlMacro());
        this.addModule(noFall = new NoFall());
        this.addModule(noRotate = new NoRotate());
        this.addModule(realLag = new RealLag());
        this.addLegacyModuleAlias(realLag, "FakeLag");

        this.addModule(antiDebuff = new AntiDebuff());
        this.addModule(antiShuffle = new AntiShuffle());
        this.addModule(new Arrows());
        this.addModule(bedESP = new BedESP());
        this.addModule(new BlockOverlay());
        this.addModule(new Animations());
        this.addModule(new BreakProgress());
        this.addModule(chams = new Chams());
        this.addModule(new CuteVisual());
        this.addModule(new HitParticles());
        this.addModule(new KillEffect());
        this.addModule(new ChestESP());
        this.addModule(freelook = new Freelook());
        this.addModule(customSuffix = new CustomSuffix());
        this.addModule(arraylist = new ArrayListModule());
        this.addModule(new Indicators());
        this.addModule(new ItemESP());
        this.addModule(new ItemPhysics());
        this.addModule(mobESP = new MobESP());
        this.addModule(new Nametags());
        this.addModule(noHurtCam = new NoHurtCam());
        this.addModule(noSneakAnimation = new NoSneakAnimation());
        this.addModule(noArmorOverlay = new NoArmorOverlay());
        this.addModule(noBreakParticles = new NoBreakParticles());
        this.addModule(playerESP = new PlayerESP());
        this.addModule(spotifyMod = new SpotifyMod());
        this.addModule(new KeyStrokes());
        this.addModule(watermark = new Watermark());
        this.addModule(targetHUD = new TargetHUD());
        this.addModule(emojify = new Emojify());
        this.addModule(chat = new Chat());
        this.addModule(scoreboard = new Scoreboard());
        this.addLegacyModuleAlias(
                emojify,
                "TargetIcon",
                "idle.miau.module.impl.render.TargetIcon"
        );
        this.addModule(fireflies = new Fireflies());
        this.addModule(new Trajectories());
        this.addModule(new TNTESP());
        this.addModule(new TNTTimer());
        this.addModule(new Tracers());
        this.addModule(new Xray());
        this.addModule(new Fullbright());

        this.addModule(antiBot = new AntiBot());
        this.addModule(weather = new Weather());

        this.addModule(scriptManager = new keystrokesmod.script.Manager());

        movementFix.enable();
        antiBot.enable();
        arraylist.enable();
        watermark.enable();
        notifications.enable();
        discordPresence.enable();
        irc.enable();
        patcher.enable();
        modules.sort(Comparator.comparing(Module::getName));
    }

    public void addModule(Module module) {
        modules.add(module);
        if (module.moduleCategory() == Module.category.scripts) {
            scriptModulesByName.put(module.getName(), module);
            scriptModulesByNormalizedName.put(normalizeModuleName(module.getName()), module);
            modulesByConfigId.put(module.getConfigId(), module);
            modulesByConfigId.put("sc-" + module.getName(), module);
            return;
        }
        modulesByName.put(module.getName(), module);
        modulesByNormalizedName.put(normalizeModuleName(module.getName()), module);
        modulesByConfigId.put(module.getConfigId(), module);
        modulesByClass.put(module.getClass(), module);
    }

    public boolean removeModule(Module module) {
        if (module == null) {
            return false;
        }
        if (module.isEnabled()) {
            module.disable();
        }
        boolean removed = modules.remove(module);
        if (!removed) {
            return false;
        }
        synchronized (organizedModules) {
            organizedModules.remove(module);
        }
        if (module.moduleCategory() == Module.category.scripts) {
            scriptModulesByName.entrySet().removeIf(entry -> entry.getValue() == module);
            scriptModulesByNormalizedName.entrySet().removeIf(entry -> entry.getValue() == module);
            modulesByConfigId.entrySet().removeIf(entry -> entry.getValue() == module);
            return true;
        }
        modulesByName.entrySet().removeIf(entry -> entry.getValue() == module);
        modulesByNormalizedName.entrySet().removeIf(entry -> entry.getValue() == module);
        modulesByConfigId.entrySet().removeIf(entry -> entry.getValue() == module);
        modulesByClass.entrySet().removeIf(entry -> entry.getValue() == module);
        return true;
    }

    private void addLegacyModuleAlias(Module module, String legacyName, String... legacyConfigIds) {
        modulesByName.put(legacyName, module);
        modulesByNormalizedName.put(normalizeModuleName(legacyName), module);
        for (String legacyConfigId : legacyConfigIds) {
            modulesByConfigId.put(legacyConfigId, module);
        }
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> inCategory(Module.category category) {
        ArrayList<Module> categoryModules = new ArrayList<>();

        for (Module module : this.getModules()) {
            if (module.moduleCategory().equals(category)) {
                categoryModules.add(module);
            }
        }

        return categoryModules;
    }

    public static Module getModule(String moduleName) {
        if (moduleName == null) {
            return null;
        }
        Module configuredModule = modulesByConfigId.get(moduleName);
        if (configuredModule != null) {
            return configuredModule;
        }
        if ("ArrayList".equalsIgnoreCase(moduleName) && arraylist != null) {
            return arraylist;
        }
        if (moduleName.regionMatches(true, 0, "sc-", 0, 3)) {
            String scriptKey = moduleName.substring(3);
            Module script = scriptModulesByName.get(scriptKey);
            if (script == null) {
                script = scriptModulesByNormalizedName.get(normalizeModuleName(scriptKey));
            }
            if (script != null) {
                return script;
            }
        }
        if (moduleName.regionMatches(true, 0, "script:", 0, 7)) {
            String scriptKey = moduleName.substring(7);
            Module script = scriptModulesByName.get(scriptKey);
            if (script == null) {
                script = scriptModulesByNormalizedName.get(normalizeModuleName(scriptKey));
            }
            if (script != null) {
                return script;
            }
        }
        Module module = modulesByName.get(moduleName);
        if (module != null) {
            return module;
        }
        module = modulesByNormalizedName.get(normalizeModuleName(moduleName));
        if (module != null) {
            return module;
        }
        module = scriptModulesByName.get(moduleName);
        if (module != null) {
            return module;
        }
        return scriptModulesByNormalizedName.get(normalizeModuleName(moduleName));
    }

    public static Module getScriptModule(String scriptName) {
        if (scriptName == null) {
            return null;
        }
        if (scriptName.regionMatches(true, 0, "sc-", 0, 3)) {
            scriptName = scriptName.substring(3);
        }
        else if (scriptName.regionMatches(true, 0, "script:", 0, 7)) {
            scriptName = scriptName.substring(7);
        }
        Module script = scriptModulesByName.get(scriptName);
        if (script == null) {
            script = scriptModulesByNormalizedName.get(normalizeModuleName(scriptName));
        }
        return script;
    }

    public static Module getModuleByConfigId(String configId) {
        return configId == null ? null : modulesByConfigId.get(configId);
    }

    public static Module getModule(Class<?> clazz) {
        return modulesByClass.get(clazz);
    }

    public static void synchronizeOrganizedModules() {
        synchronized (organizedModules) {
            organizedModules.clear();
            for (Module module : modules) {
                if (module != null && module.isEnabled()) {
                    organizedModules.add(module);
                }
            }
        }
        sort();
    }

    public static void sort() {
        if (organizedModules.isEmpty()) {
            return;
        }

        final MiauFontRenderer hudFont = ArrayListModule.getHudFontRenderer();
        synchronized (organizedModules) {
            final Map<Module, Integer> widths = new HashMap<>(organizedModules.size());
            for (Module module : organizedModules) {
                widths.put(module, hudFont.getStringWidth(ArrayListModule.getHudRenderText(module)));
            }
            organizedModules.sort((left, right) -> Integer.compare(widths.get(right), widths.get(left)));
        }
    }

    private static String normalizeModuleName(String moduleName) {
        if (moduleName == null) {
            return "";
        }

        StringBuilder normalized = new StringBuilder(moduleName.length());
        for (int i = 0; i < moduleName.length(); i++) {
            char character = moduleName.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                normalized.append(Character.toLowerCase(character));
            }
        }
        return normalized.toString();
    }
}
