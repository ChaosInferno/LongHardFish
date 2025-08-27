package org.aincraft.domain;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.aincraft.container.Rarity;

record RarityImpl(double baseWeight, Component label, TextColor color, Key key) implements
    Rarity {

}
