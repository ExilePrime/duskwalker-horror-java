package com.duskwalker.bedrock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @minecraft/server-ui karsiligi. Java'da vanilla istemciye form gonderilemedigi icin binding bunlari
 * sunucu tarafli sandik-menusu (GUI) olarak gosterir; API Bedrock ile ayni kalir.
 *   Bedrock: new ActionFormData().title(..).body(..).button(..).show(player).then(res => ...)
 *   Java:    new Forms.Action().title(..).body(..).button(..).show(player, res -> ...)
 */
public final class Forms {
    private Forms() {}

    public interface Form<R extends Response> {
        /** Yanit (iptal dahil) tek sefer handler'a verilir. Oyuncu baska bir menudeyse iptal sebebi "UserBusy" olur. */
        void show(Player player, Consumer<R> handler);
    }

    public static class Response {
        public boolean canceled;
        /** "UserClosed" | "UserBusy" | null */
        public String cancelationReason;
    }

    public static final class ActionResponse extends Response {
        public int selection;            // basilan dugmenin sirasi
    }

    public static final class ModalResponse extends Response {
        /** Alan sirasina gore: toggle -> Boolean, slider -> Double, dropdown -> Integer (secilen sira), textField -> String */
        public List<Object> formValues = new ArrayList<>();
    }

    // ---------------------------------------------------------------- Action
    public static final class Action implements Form<ActionResponse> {
        public String title = "";
        public String body = "";
        public final List<String> buttons = new ArrayList<>();

        public Action title(String t) { this.title = t; return this; }
        public Action body(String b) { this.body = b; return this; }
        public Action button(String text) { buttons.add(text); return this; }

        @Override public void show(Player player, Consumer<ActionResponse> handler) {
            Platform.get().showActionForm(player, this, handler);
        }
    }

    // ---------------------------------------------------------------- Modal
    public static final class Modal implements Form<ModalResponse> {
        public enum Kind { TOGGLE, SLIDER, DROPDOWN, TEXT }

        public static final class Field {
            public Kind kind;
            public String label;
            public boolean toggleDefault;
            public double min, max, step, sliderDefault;
            public List<String> options = new ArrayList<>();
            public int dropdownDefault;
            public String textPlaceholder = "", textDefault = "";
        }

        public String title = "";
        public final List<Field> fields = new ArrayList<>();

        public Modal title(String t) { this.title = t; return this; }

        public Modal toggle(String label, boolean def) {
            Field f = new Field(); f.kind = Kind.TOGGLE; f.label = label; f.toggleDefault = def; fields.add(f); return this;
        }
        public Modal slider(String label, double min, double max, double step, double def) {
            Field f = new Field(); f.kind = Kind.SLIDER; f.label = label; f.min = min; f.max = max; f.step = step; f.sliderDefault = def; fields.add(f); return this;
        }
        public Modal dropdown(String label, List<String> options, int def) {
            Field f = new Field(); f.kind = Kind.DROPDOWN; f.label = label; f.options = new ArrayList<>(options); f.dropdownDefault = def; fields.add(f); return this;
        }
        public Modal textField(String label, String placeholder, String def) {
            Field f = new Field(); f.kind = Kind.TEXT; f.label = label; f.textPlaceholder = placeholder; f.textDefault = def; fields.add(f); return this;
        }

        @Override public void show(Player player, Consumer<ModalResponse> handler) {
            Platform.get().showModalForm(player, this, handler);
        }
    }
}
