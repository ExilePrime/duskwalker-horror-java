package com.duskwalker.fabric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.duskwalker.bedrock.Forms;
import com.duskwalker.bedrock.Log;
import com.duskwalker.bedrock.Sys;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * Forms.Action / Forms.Modal -> sunucu tarafli sandik menusu. Menu ogeleri alinamaz/tasinamaz (her tiklama iptal edilir ve yeniden senkronlanir).
 * Cevap ve kapatma geri cagrilari her zaman bir SONRAKI sunucu tikinde (Sys.run) calisir: tiklama paketi islenirken menu kapatilmaz.
 * Oyuncu baska bir menudeyse ya da zaten bir formu varsa iptal sebebi "UserBusy".
 */
public final class FormGui {
    private FormGui() {}

    private static final Map<UUID, Controller> ACTIVE = new HashMap<>();

    // ================================================================= genel
    public static void showAction(com.duskwalker.bedrock.Player player, Forms.Action form, Consumer<Forms.ActionResponse> handler) {
        ServerPlayer sp = ((McPlayer) player).sp();
        if (busy(sp)) { busyReply(handler, new Forms.ActionResponse()); return; }
        open(sp, new ActionController(sp, form, handler), form.title);
    }

    public static void showModal(com.duskwalker.bedrock.Player player, Forms.Modal form, Consumer<Forms.ModalResponse> handler) {
        ServerPlayer sp = ((McPlayer) player).sp();
        if (busy(sp)) { busyReply(handler, new Forms.ModalResponse()); return; }
        open(sp, new ModalController(sp, form, handler), form.title);
    }

    /** Sunucu kapanirken / sifirlanirken. */
    public static void reset() { ACTIVE.clear(); }

    private static boolean busy(ServerPlayer sp) {
        return ACTIVE.containsKey(sp.getUUID()) || sp.containerMenu != sp.inventoryMenu;
    }

    private static <R extends Forms.Response> void busyReply(Consumer<R> handler, R res) {
        res.canceled = true;
        res.cancelationReason = "UserBusy";
        Sys.run(() -> handler.accept(res));
    }

    private static void open(ServerPlayer sp, Controller c, String title) {
        ACTIVE.put(sp.getUUID(), c);
        SimpleContainer box = new SimpleContainer(c.rows() * 9);
        c.render(box);
        Component t = Component.literal(title == null || title.isEmpty() ? " " : title);
        java.util.OptionalInt id = sp.openMenu(new SimpleMenuProvider((cid, inv, pl) -> new FormMenu(cid, inv, box, c), t));
        if (id.isEmpty()) {
            ACTIVE.remove(sp.getUUID());
            c.failBusy();
        }
    }

    // ================================================================= esya yardimcilari
    static ItemStack icon(Item item, String name, List<String> lore) {
        ItemStack st = new ItemStack(item);
        st.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withItalic(false)));
        if (lore != null && !lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String l : lore) lines.add(Component.literal(l).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)));
            st.set(DataComponents.LORE, new ItemLore(lines));
        }
        return st;
    }

    /** Uzun metni ~38 karakterlik satirlara boler (\n korunur). */
    static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String para : text.split("\n", -1)) {
            StringBuilder cur = new StringBuilder();
            for (String w : para.split(" ")) {
                if (cur.length() > 0 && cur.length() + 1 + w.length() > 38) { out.add(cur.toString()); cur.setLength(0); }
                if (cur.length() > 0) cur.append(' ');
                cur.append(w);
            }
            out.add(cur.toString());
        }
        return out;
    }

    private static MenuType<ChestMenu> typeFor(int rows) {
        switch (rows) {
            case 1: return MenuType.GENERIC_9x1;
            case 2: return MenuType.GENERIC_9x2;
            case 3: return MenuType.GENERIC_9x3;
            case 4: return MenuType.GENERIC_9x4;
            case 5: return MenuType.GENERIC_9x5;
            default: return MenuType.GENERIC_9x6;
        }
    }

    // ================================================================= menu
    static final class FormMenu extends ChestMenu {
        private final Controller ctl;
        private final int topSlots;

        FormMenu(int id, Inventory inv, SimpleContainer box, Controller ctl) {
            super(typeFor(ctl.rows()), id, inv, box, ctl.rows());
            this.ctl = ctl;
            this.topSlots = ctl.rows() * 9;
            ctl.menu = this;
        }

        @Override
        public void clicked(int slotId, int button, ClickType type, net.minecraft.world.entity.player.Player player) {
            try {
                if (slotId >= 0 && slotId < topSlots && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE || type == ClickType.SWAP
                        || type == ClickType.CLONE || type == ClickType.PICKUP_ALL || type == ClickType.THROW)) {
                    ctl.click(slotId, button);
                }
            } catch (Throwable t) {
                Log.warn("form click failed: " + t);
            }
            this.setCarried(ItemStack.EMPTY);
            this.sendAllDataToRemote();
        }

        @Override
        public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) { return ItemStack.EMPTY; }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }

        @Override
        public void removed(net.minecraft.world.entity.player.Player player) {
            super.removed(player);
            ctl.closed();
        }
    }

    // ================================================================= denetleyici taban sinifi
    abstract static class Controller {
        final ServerPlayer player;
        FormMenu menu;
        boolean answered;

        Controller(ServerPlayer player) { this.player = player; }

        abstract int rows();
        abstract void render(SimpleContainer box);
        abstract void click(int slot, int button);
        /** Cevap vermeden kapandi (ESC / baska menu / ayrilma). */
        abstract void cancel(String reason);
        abstract void failBusy();

        void refresh() {
            if (menu != null) {
                SimpleContainer box = menuBox();
                render(box);
            }
        }

        SimpleContainer menuBox() { return (SimpleContainer) menuContainer(); }

        private net.minecraft.world.Container menuContainer() {
            return menu.getContainer();
        }

        void closed() {
            if (answered) return;
            answered = true;
            Sys.run(() -> {
                ACTIVE.remove(player.getUUID(), this);
                cancel("UserClosed");
            });
        }

        /** Cevabi bir sonraki tikte ver: menuyu kapat, kaydi sil, geri cagriyi calistir. */
        void answer(Runnable respond) {
            if (answered) return;
            answered = true;
            Sys.run(() -> {
                try {
                    if (menu != null && player.containerMenu == menu) player.closeContainer();
                } catch (Throwable t) {
                    Log.warn("form close failed: " + t);
                }
                ACTIVE.remove(player.getUUID(), this);
                respond.run();
            });
        }
    }

    // ================================================================= ACTION
    static final class ActionController extends Controller {
        private final Forms.Action form;
        private final Consumer<Forms.ActionResponse> handler;
        private final int rows;
        private final boolean paged;
        private final boolean hasBody;
        private final int firstBtnRow;
        private final int perPage;
        private int page;

        ActionController(ServerPlayer p, Forms.Action form, Consumer<Forms.ActionResponse> handler) {
            super(p);
            this.form = form;
            this.handler = handler;
            int n = form.buttons.size();
            this.hasBody = form.body != null && !form.body.isEmpty();
            int bodyRows = hasBody ? 1 : 0;
            int btnRows = (n + 8) / 9;
            if (bodyRows + btnRows <= 6) {
                this.rows = Math.max(1, bodyRows + btnRows);
                this.paged = false;
                this.perPage = Math.max(1, n);
                this.firstBtnRow = bodyRows;
            } else {
                this.rows = 6;
                this.paged = true;
                this.perPage = 36;
                this.firstBtnRow = 1;
            }
        }

        @Override int rows() { return rows; }

        private int pages() { return Math.max(1, (form.buttons.size() + perPage - 1) / perPage); }

        @Override
        void render(SimpleContainer box) {
            box.clearContent();
            if (hasBody || paged) {
                List<String> lore = wrap(form.body);
                if (lore.size() > 24) lore = new ArrayList<>(lore.subList(0, 24));
                box.setItem(4, icon(Items.BOOK, form.title == null || form.title.isEmpty() ? " " : form.title, lore));
            }
            int base = page * perPage;
            int capacity = paged ? 36 : rows * 9 - firstBtnRow * 9;
            for (int i = 0; i < capacity; i++) {
                int idx = base + i;
                if (idx >= form.buttons.size()) break;
                String text = form.buttons.get(idx);
                String[] lines = text.split("\n", -1);
                List<String> lore = new ArrayList<>();
                for (int k = 1; k < lines.length; k++) lore.add(lines[k]);
                box.setItem(firstBtnRow * 9 + i, icon(Items.PAPER, lines[0].isEmpty() ? " " : lines[0], lore));
            }
            if (paged) {
                box.setItem(49, icon(Items.CLOCK, "§f" + (page + 1) + " / " + pages(), List.of()));
                if (page > 0) box.setItem(45, icon(Items.ARROW, "§e<", List.of()));
                if (page < pages() - 1) box.setItem(53, icon(Items.ARROW, "§e>", List.of()));
            }
        }

        @Override
        void click(int slot, int button) {
            if (paged) {
                if (slot == 45 && page > 0) { page--; refresh(); return; }
                if (slot == 53 && page < pages() - 1) { page++; refresh(); return; }
            }
            int rel = slot - firstBtnRow * 9;
            if (rel < 0) return;
            if (paged && slot >= 45) return;
            int idx = page * perPage + rel;
            if (idx < 0 || idx >= form.buttons.size()) return;
            Forms.ActionResponse r = new Forms.ActionResponse();
            r.selection = idx;
            answer(() -> handler.accept(r));
        }

        @Override
        void cancel(String reason) {
            Forms.ActionResponse r = new Forms.ActionResponse();
            r.canceled = true;
            r.cancelationReason = reason;
            handler.accept(r);
        }

        @Override void failBusy() { answered = true; busyReply(handler, new Forms.ActionResponse()); }
    }

    // ================================================================= MODAL
    static final class ModalController extends Controller {
        private static final int PER_PAGE = 5;
        private final Forms.Modal form;
        private final Consumer<Forms.ModalResponse> handler;
        private final int rows;
        private final boolean paged;
        private final boolean[] on;
        private final double[] num;
        private final int[] sel;
        private final String[] txt;
        private int page;

        ModalController(ServerPlayer p, Forms.Modal form, Consumer<Forms.ModalResponse> handler) {
            super(p);
            this.form = form;
            this.handler = handler;
            int n = form.fields.size();
            this.on = new boolean[n];
            this.num = new double[n];
            this.sel = new int[n];
            this.txt = new String[n];
            for (int i = 0; i < n; i++) {
                Forms.Modal.Field f = form.fields.get(i);
                switch (f.kind) {
                    case TOGGLE: on[i] = f.toggleDefault; break;
                    case SLIDER: num[i] = snap(f, f.sliderDefault); break;
                    case DROPDOWN: sel[i] = f.options.isEmpty() ? 0 : Math.max(0, Math.min(f.options.size() - 1, f.dropdownDefault)); break;
                    case TEXT: txt[i] = f.textDefault == null ? "" : f.textDefault; break;
                    default: break;
                }
            }
            this.paged = n > PER_PAGE;
            this.rows = paged ? 6 : Math.min(6, n + 1);
        }

        @Override int rows() { return rows; }

        private int pages() { return Math.max(1, (form.fields.size() + PER_PAGE - 1) / PER_PAGE); }

        private static double stepOf(Forms.Modal.Field f) { return f.step > 0 ? f.step : 1.0; }

        private static double snap(Forms.Modal.Field f, double v) {
            double st = stepOf(f);
            double s = Math.round((v - f.min) / st) * st + f.min;
            return Math.max(f.min, Math.min(f.max, s));
        }

        private static String fmt(double v) {
            if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
            return String.format(Locale.ROOT, "%.2f", v);
        }

        @Override
        void render(SimpleContainer box) {
            box.clearContent();
            int base = page * PER_PAGE;
            for (int r = 0; r < PER_PAGE; r++) {
                int i = base + r;
                if (i >= form.fields.size()) break;
                Forms.Modal.Field f = form.fields.get(i);
                int row = r * 9;
                String label = f.label == null || f.label.isEmpty() ? " " : f.label;
                switch (f.kind) {
                    case TOGGLE:
                        box.setItem(row, icon(on[i] ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE,
                                label + "§r: " + (on[i] ? "§aON" : "§cOFF"), List.of("§7Click to toggle")));
                        break;
                    case SLIDER: {
                        double st = stepOf(f);
                        box.setItem(row, icon(Items.CLOCK, label + "§r: §e" + fmt(num[i]), List.of("§7" + fmt(f.min) + " - " + fmt(f.max))));
                        box.setItem(row + 1, icon(Items.RED_STAINED_GLASS_PANE, "§c-" + fmt(st * 10), List.of()));
                        box.setItem(row + 2, icon(Items.ORANGE_STAINED_GLASS_PANE, "§6-" + fmt(st), List.of()));
                        box.setItem(row + 3, icon(Items.LIME_STAINED_GLASS_PANE, "§a+" + fmt(st), List.of()));
                        box.setItem(row + 4, icon(Items.GREEN_STAINED_GLASS_PANE, "§2+" + fmt(st * 10), List.of()));
                        break;
                    }
                    case DROPDOWN: {
                        List<String> lore = new ArrayList<>();
                        for (int k = 0; k < f.options.size(); k++) lore.add((k == sel[i] ? "§a> " : "§7  ") + f.options.get(k));
                        String cur = f.options.isEmpty() ? "" : f.options.get(sel[i]);
                        box.setItem(row, icon(Items.COMPARATOR, label + "§r: §e" + cur, lore));
                        break;
                    }
                    case TEXT:
                        box.setItem(row, icon(Items.NAME_TAG, label + "§r: §e" + txt[i], List.of("§7(text fields cannot be edited here)")));
                        break;
                    default: break;
                }
            }
            int ctl = (rows - 1) * 9;
            box.setItem(ctl + 3, icon(Items.BARRIER, "§cCancel", List.of()));
            box.setItem(ctl + 5, icon(Items.EMERALD_BLOCK, "§aSave", List.of()));
            if (paged) {
                box.setItem(ctl + 4, icon(Items.CLOCK, "§f" + (page + 1) + " / " + pages(), List.of()));
                if (page > 0) box.setItem(ctl, icon(Items.ARROW, "§e<", List.of()));
                if (page < pages() - 1) box.setItem(ctl + 8, icon(Items.ARROW, "§e>", List.of()));
            }
        }

        @Override
        void click(int slot, int button) {
            int row = slot / 9, col = slot % 9;
            int ctl = rows - 1;
            if (row == ctl) {
                if (col == 3) { answer(() -> cancel("UserClosed")); return; }
                if (col == 5) { save(); return; }
                if (paged && col == 0 && page > 0) { page--; refresh(); return; }
                if (paged && col == 8 && page < pages() - 1) { page++; refresh(); return; }
                return;
            }
            int i = page * PER_PAGE + row;
            if (i >= form.fields.size()) return;
            Forms.Modal.Field f = form.fields.get(i);
            switch (f.kind) {
                case TOGGLE:
                    if (col == 0) { on[i] = !on[i]; refresh(); }
                    break;
                case DROPDOWN:
                    if (col == 0 && !f.options.isEmpty()) {
                        int n = f.options.size();
                        sel[i] = button == 1 ? Math.floorMod(sel[i] - 1, n) : (sel[i] + 1) % n;
                        refresh();
                    }
                    break;
                case SLIDER: {
                    double st = stepOf(f);
                    double d = col == 1 ? -10 * st : col == 2 ? -st : col == 3 ? st : col == 4 ? 10 * st : 0;
                    if (d != 0) { num[i] = snap(f, num[i] + d); refresh(); }
                    break;
                }
                default: break;
            }
        }

        private void save() {
            Forms.ModalResponse r = new Forms.ModalResponse();
            for (int i = 0; i < form.fields.size(); i++) {
                Forms.Modal.Field f = form.fields.get(i);
                switch (f.kind) {
                    case TOGGLE: r.formValues.add(on[i]); break;
                    case SLIDER: r.formValues.add(num[i]); break;
                    case DROPDOWN: r.formValues.add(sel[i]); break;
                    case TEXT: r.formValues.add(txt[i]); break;
                    default: r.formValues.add(null); break;
                }
            }
            answer(() -> handler.accept(r));
        }

        @Override
        void cancel(String reason) {
            Forms.ModalResponse r = new Forms.ModalResponse();
            r.canceled = true;
            r.cancelationReason = reason;
            handler.accept(r);
        }

        @Override void failBusy() { answered = true; busyReply(handler, new Forms.ModalResponse()); }
    }
}
