package net.java.faker.ui.tab;

import net.java.faker.ui.GBC;
import net.java.faker.ui.I18n;
import net.java.faker.ui.UITab;
import net.java.faker.ui.Window;

import javax.swing.*;
import java.awt.*;

import static net.java.faker.ui.Window.BORDER_PADDING;

public class UISettingsTab extends UITab {

    public UISettingsTab(final Window frame) {
        super(frame, "ui_settings");
    }

    @Override
    protected void init(JPanel contentPane) {
        JPanel body = new JPanel();
        body.setLayout(new GridBagLayout());

        int gridy = 0;
        {
            JLabel languageLabel = new JLabel();
            I18n.link(languageLabel, "tab.ui_settings.language.label");
            GBC.create(body).grid(0, gridy++).insets(BORDER_PADDING, BORDER_PADDING, 0, BORDER_PADDING).anchor(GBC.NORTHWEST).add(languageLabel);

            JComboBox<String> language = new JComboBox<>(I18n.getAvailableLocales().toArray(new String[0]));
            language.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    if (value instanceof String locale) {
                        value = "<html><b>" + I18n.getSpecific(locale, "language.name") + "</b> (" + I18n.get("tab.ui_settings.language.completion", I18n.getSpecific(locale, "language.completion")) + ")</html>";
                    }
                    return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                }
            });
            language.setSelectedItem(I18n.getCurrentLocale());
            language.addActionListener(event -> {
                if (!(language.getSelectedItem() instanceof String locale)) return;
                if (locale.equals(I18n.getCurrentLocale())) return;
                I18n.setLocale(locale);
                I18n.update();
            });
            GBC.create(body).grid(0, gridy++).weightx(1).insets(0, BORDER_PADDING, 0, BORDER_PADDING).fill(GBC.HORIZONTAL).add(language);
        }

        contentPane.setLayout(new BorderLayout());
        contentPane.add(body, BorderLayout.NORTH);
    }

}