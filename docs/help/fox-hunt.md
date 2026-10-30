<!-- Generated from app/src/main/java/com/macdmr/radio/ui/HelpView.java by HelpExportTest. Do not edit by hand. -->

# Fox hunt

*Pages · 22 of 34*

Find a hidden transmitter, or be the fox.

## Opening it

PKT → Fox hunt, or the menu of the new intercom look → Fox hunt. Tune the fox's channel first: the hunt listens on the current channel.

## The readout

The big number is the signal strength in dBm (higher, closer to zero, is stronger), with the S-meter and the peak since you opened it or cleared. Beeper on clicks through the phone faster and higher as the signal rises, so you can hunt without looking. Pointing shows the compass bearing of the top of the phone, corrected to true north.

## Marks and the estimate

Hold the phone against your chest and turn slowly: your body blocks the signal from behind, so the strongest reading is when you face the fox. Point the top of the phone that way and tap Mark: the reading, your position and the bearing are saved. After two or more marks from different places, the app works out where the bearing lines cross, giving stronger readings more weight, and shows the distance and direction from you. The map shows your marks (cyan weak, amber medium, red strong; a hollow ring is a mark where the radio heard nothing) with their bearing lines and the estimate as a green ring; Plot in its corner switches to a compact plot around you, which also takes over by itself when no map tiles load (offline). Without bearings, the strongest mark is the hint. Open in maps shows the estimate in your map app. Marks are refused while you transmit (the reading would be your own signal). Marks stay until Clear marks, even if you close the sheet.

## Prediction

Three layers, each switched on or off under Prediction (SignalDeck remembers the choice). Bearings (on at first): each bearing line becomes a band of likely places about 15 degrees wide either side, because a compass held near the body is good to about that; with Bearings alone the estimate is the same crossing as before. Heat map (on at first): uses only how the signal strength changes from mark to mark, no bearings needed. It needs 3 or more marks at least 100 m apart, with at least 2 of them above the radio's noise floor; until then the line under the switches says what is missing. For every place on a grid around your marks (2 by 2 km with 25 m squares, made bigger when the best place sits at its edge) it asks how well a transmitter there explains your readings, with its power unknown and the signal falling off between open-ground and cluttered rates (the rate is chosen per hunt); a mark at the noise floor only says the fox is farther away than that. The map shades the likely area, cyan to red as it gets likelier, with a bright outline around the place the fox is in with 50 % probability and a faint one at 90 %. With both on, the two are combined into one estimate, which is usually better than either alone. The estimate line names the layers it used and a confidence word from the size of the 50 % outline: good (no bigger than a circle of about 100 m radius), fair (up to about 300 m), rough (bigger: usual for the heat map alone). With both off the sheet works as it did before. Honest limits: strength-based estimates are rough: walls, cars and your body change readings by 10 dB or more. Take marks in the open, spread out around the area rather than in a line, and hold the phone the same way every time. Near the fox, use Off-frequency so the readings do not all sit at the top of the scale.

## Terrain

Terrain (off at first, because it downloads and costs battery) makes the heat map take the ground into account: for every place on the grid and every mark it looks at the ground heights in between and adds the loss a hill or ridge causes (the same model as the coverage maps), so places a strong mark could not have heard from behind a ridge drop out, and a weak mark behind a hill no longer looks farther away than it is. It works on the heat map, so it needs Heat map on. The ground heights come from the open Terrain Tiles: the first time in an area the sheet says there is no terrain for this area yet and offers Download terrain with its size (usually well under 1 MB); they are kept on the phone (shared with the coverage maps), so later hunts there work offline. The fox's antenna height changes how much a ridge hides it: tap the height button to choose on the ground (1.5 m, a handheld or a box in the grass), on a roof (10 m) or on a tower (30 m). It is worked out on 100 m blocks first and then on the 25 m squares of the likeliest blocks, and remembered between marks, so a new mark costs only its own paths. The ground model knows hills, not buildings or trees, and its heights are about 55 m apart, so a small bank or a wall does not show.

## Close to the fox

Near the transmitter every reading is at the top of the scale. Off-frequency listens 10, 20 or 30 kHz above the channel, where the fox's signal is much weaker, so the readings start to change again as you move. Closing the sheet tunes back to the channel.

## On the Unihertz Atom XL

Its radio sits behind a USB bridge chip that stops the sound when a command crosses the link while a reception plays. The hunt reads the signal anyway, because it needs the readings, so on this phone readings during a reception may cut its audio for a few seconds (SignalDeck restarts the bridge by itself). The bearing and the readout are not affected.

## Be the fox

Start the fox beacon sends 5 seconds of beeps on the current channel every minute until you stop it or change channel; on amateur channels your callsign follows in Morse. PKT lights while it runs. Hide the phone, with a full battery.

[All topics](README.md)
