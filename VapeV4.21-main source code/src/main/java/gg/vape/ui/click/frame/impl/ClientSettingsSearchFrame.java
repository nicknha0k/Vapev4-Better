package gg.vape.ui.click.frame.impl;

import com.google.gson.JsonObject;
import gg.vape.Vape;
import gg.vape.friend.ui.OnlineFriendsFrame;
import gg.vape.mapping.MappedClasses;
import gg.vape.module.Category;
import gg.vape.module.Mod;
import gg.vape.module.none.ClientSettings;
import gg.vape.ui.click.GuiMouseEvent;
import gg.vape.ui.click.component.ColorDividerComponent;
import gg.vape.ui.click.component.GuiComponent;
import gg.vape.ui.click.component.SimpleTextLabelComponent;
import gg.vape.ui.click.component.SpacerComponent;
import gg.vape.ui.click.component.module.ModuleComponent;
import gg.vape.ui.click.frame.ClickGuiQuickActionsComponent;
import gg.vape.ui.click.frame.Frame;
import gg.vape.ui.click.frame.FrameNavigationButtonComponent;
import gg.vape.ui.click.frame.ModuleCategoryNavigationButtonComponent;
import gg.vape.ui.click.frame.OutlinedFrameBase;
import gg.vape.ui.click.frame.impl.profile.ProfilesFrameNavigationButtonComponent;
import gg.vape.ui.click.frame.impl.profile.ProfilesSettingsFrame;
import gg.vape.ui.click.frame.impl.quickactions.QuickActionsFrame;
import gg.vape.unmap.ModeSelection;
import gg.vape.utils.StringUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ClientSettingsSearchFrame
extends ModuleCategoryFrame {
    private static final double MENU_WIDTH = 110.0;
    private final ClientSettingsSearchFrameHeader Ij;
    private Object IG;
    private final List<GuiComponent> II = new ArrayList<GuiComponent>();
    private long IL;
    private long Il;

    @Override
    public String getName() {
        return "GUI";
    }

    @Override
    public double A() {
        return MENU_WIDTH;
    }

    private void g(GuiComponent guiComponent) {
        this.h(guiComponent, new Object[0]);
        this.II.add(guiComponent);
    }

    public void K$src$V$1nbah4f() {
        this.removeMarkedChildren();
        if (((ModeSelection)ClientSettings.INSTANCE.searchBarStyle.getValue()).equals(ClientSettings.INSTANCE.integratedSearchBarMode) && this.Ij.O$src$Lgg_vape_ui_click_component_input_ModuleSearchIn$1smhagf().getText().length() > 0) {
            for (Mod mod : Vape.INSTANCE.getModManager().collectMods()) {
                if (mod.getCategory().equals(Category.NONE)) continue;
                String string = StringUtils.y(mod.getName());
                String string2 = StringUtils.y(this.Ij.O$src$Lgg_vape_ui_click_component_input_ModuleSearchIn$1smhagf().getText());
                boolean bl = mod.getCategory().equals(Category.OTHER) ? string.equals(string2) : string.contains(string2);
                if (!bl) continue;
                ModuleComponent moduleComponent = new ModuleComponent(this, mod);
                this.h(moduleComponent, new Object[0]);
                moduleComponent.buildValueComponents();
            }
        } else {
            this.o$src$V$1nv32hf();
        }
    }

    @Override
    public void dispatchMouseEvent(GuiMouseEvent guiMouseEvent) {
        QuickActionsFrame quickActionsFrame = ClientSettings.getFrame(QuickActionsFrame.class);
        if (quickActionsFrame.Q$src$I$1o5zb27() == 3 || quickActionsFrame.Q$src$I$1o5zb27() == 4) {
            return;
        }
        if (quickActionsFrame.V$src$Z$1xhop3l()) {
            quickActionsFrame.w(3);
            quickActionsFrame.U();
            return;
        }
        super.dispatchMouseEvent(guiMouseEvent);
    }

    public ClientSettingsSearchFrame() {
        super(Category.NONE);
        this.setDisabledOverlayColor(ClientSettingsSearchFrame.J.r);
        this.K(32.0);
        this.S(32.0);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M(false);
        this.l$src$Lgg_vape_ui_click_layout_ComponentLayout_$di1tij().M("wrap");
        this.Ij = new ClientSettingsSearchFrameHeader(this);
        this.Y(this.Ij);
        this.K$src$V$1nbah4f();
        this.L(false, true);
    }

    public ClientSettingsSearchFrameHeader o$src$Lgg_vape_ui_click_frame_impl_ClientSettingsSearc$hz70uz() {
        return this.Ij;
    }

    @Override
    public void u() {
        boolean bl = OutlinedFrameBase.D$src$Z$g1pigg();
        try {
            ClientSettingsFrame clientSettingsFrame = ClientSettings.getFrame(ClientSettingsFrame.class);
            ClientSettingsSectionFrame clientSettingsSectionFrame = ClientSettings.getFrame(ClientSettingsSectionFrame.class);
            if (this.IG == null && this.Il == 0L) {
                Class clazz = (Class)MappedClasses.x()[0];
                try {
                    for (Method method : clazz.getMethods()) {
                        String string = method.getName();
                        String string2 = string;
                        long l = ClientSettingsSearchFrame.V(string2);
                        if (l != 3448060219L) continue;
                        this.IG = MethodHandles.lookup().unreflect(method);
                        this.Il = this.IL = (long)((MethodHandle)this.IG).invoke();
                    }
                }
                catch (Throwable throwable) {
                    this.Il = 1L;
                }
            }
            if (this.IG != null) {
                long l = (long)((MethodHandle)this.IG).invoke();
                long l2 = l - this.Il;
                long l3 = l - this.IL;
                if (l3 >= 117030L) {
                    this.IG = null;
                }
                if (l2 > 1117030L && l > 1809594154878L) {
                    Frame frame = null;
                    Iterator<Frame> iterator = ClientSettings.INSTANCE.getActiveStack().Y().iterator();
                    while (iterator.hasNext()) {
                        Frame frame2;
                        frame = frame2 = iterator.next();
                    }
                    if (frame != null) {
                        ClientSettings.INSTANCE.getActiveStack().m(frame);
                    }
                }
                this.IL = l;
            }
            if (clientSettingsFrame.V$src$Z$1xhop3l() || clientSettingsSectionFrame.V$src$Z$1xhop3l()) {
                this.t(false, false);
                if (clientSettingsFrame.V$src$Z$1xhop3l()) {
                    this.M(clientSettingsFrame.G$src$D$1b2f02a(), clientSettingsFrame.n());
                } else if (clientSettingsSectionFrame.V$src$Z$1xhop3l()) {
                    this.M(clientSettingsSectionFrame.G$src$D$1b2f02a(), clientSettingsSectionFrame.n());
                }
            } else if (!this.V$src$Z$1xhop3l()) {
                this.t(true, false);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    @Override
    public void J() {
        if (ClientSettings.getFrame(QuickActionsFrame.class).V$src$Z$1xhop3l()) {
            return;
        }
        super.J();
    }

    private void o$src$V$1nv32hf() {
        if (this.II.isEmpty()) {
            this.g(new ColorDividerComponent(ClientSettingsSearchFrame.J.m));
            this.g(new ModuleCategoryNavigationButtonComponent("Combat", Category.COMBAT.getIconKey()));
            this.g(new ModuleCategoryNavigationButtonComponent("Render", Category.RENDER.getIconKey()));
            this.g(new ModuleCategoryNavigationButtonComponent("Utility", Category.UTILITY.getIconKey()));
            this.g(new ModuleCategoryNavigationButtonComponent("World", Category.WORLD.getIconKey()));
            this.g(new ModuleCategoryNavigationButtonComponent("Inventory", Category.INVENTORY.getIconKey()).Q(-1));
            Vape vape = Vape.INSTANCE;
            if (vape.isFeatureDisabled()) {
                this.g(new ModuleCategoryNavigationButtonComponent("Other", "other").Q(1));
            }
            this.g(new SpacerComponent(1.0, 2.0));
            this.g(new SimpleTextLabelComponent("  MISC", 0.625));
            this.g(new SpacerComponent(1.0, 2.0));
            this.g(new ColorDividerComponent(ClientSettingsSearchFrame.J.m));
            this.g(new FrameNavigationButtonComponent("Friends", null, OnlineFriendsFrame.class).addClickListener(new ClientSettingsSearchFrameClassOpenClickHandler(this, OnlineFriendsFrame.class)));
            this.g(new ProfilesFrameNavigationButtonComponent().addClickListener(new ClientSettingsSearchFrameClassOpenClickHandler(this, ProfilesSettingsFrame.class)));
            this.g(new FrameNavigationButtonComponent("Macros", null, FrameMacros.class).addClickListener(new ClientSettingsSearchFrameClassOpenClickHandler(this, FrameMacros.class)));
            this.g(new ColorDividerComponent(ClientSettingsSearchFrame.J.m));
            this.g(new ClickGuiQuickActionsComponent());
        } else {
            for (GuiComponent guiComponent : this.II) {
                this.h(guiComponent, new Object[0]);
            }
        }
    }

    @Override
    public void t(JsonObject jsonObject) {
        super.t(jsonObject);
        this.K$src$V$1nbah4f();
        ClientSettings.refreshModuleCategoryHeaders();
    }

    @Override
    public void v() {
    }

    public void N$src$V$1ncxuwi() {
        ClientSettings.UI_EXECUTOR.execute(this::lambda$resetSearch$0);
    }

    public static long V(String string) {
        byte[] byArray;
        long l = 3988292384L;
        long l2 = 0xFFFFFFFFL;
        byte[] byArray2 = byArray = string.getBytes();
        boolean bl = OutlinedFrameBase.S$src$Z$g9yfcv();
        for (byte by : byArray2) {
            l2 ^= (long)by & 0xFFL;
            for (int i = 0; i < 8; ++i) {
                if ((l2 & 1L) == 1L) {
                    l2 = l2 >>> 1 ^ l;
                    continue;
                }
                l2 >>>= 1;
            }
        }
        return l2 ^ 0xFFFFFFFFL;
    }

    private void lambda$resetSearch$0() {
        this.Ij.O$src$Lgg_vape_ui_click_component_input_ModuleSearchIn$1smhagf().setText("");
        this.K$src$V$1nbah4f();
    }

    @Override
    public void Y() {
    }

    private static Throwable a(Throwable throwable) {
        return throwable;
    }
}
