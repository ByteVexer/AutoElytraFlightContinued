# Auto Elytra Flight Continued

Flies your elytra for you. While gliding, press **R** (rebindable) and the mod alternates a
shallow dive with a sharp pull-up, which gains altitude under vanilla elytra physics without
fireworks. It is client-side and only moves your pitch, as a mouse would.

- Fabric, Minecraft 26.2, needs Fabric API.
- Press the same key when you are not gliding to open the settings.
- Turning it on from low speed starts with a dive of roughly 70 blocks: have clear air below.
- It does not look at terrain. Use it in open sky.
- It warns you (sound, message, red HUD line) when your elytra is nearly worn out. It never
  lands for you and never switches itself off in mid-air.
- Some servers treat automated flight as against their rules. Check before you use it.

## Settings

Press the toggle key while you are not gliding. Everything on the screen is saved to
`config/autoelytraflightcontinued.json` when you close it.

| Setting | Default | What it does |
|---|---|---|
| Speed limit | Off | Flies a slower cycle that stays under the limit, for servers that punish fast flight |
| Limit | 39.5 m/s | The server's limit. Dives end 2.5 m/s below it |
| Altitude ceiling | Off | Stops climbing near the ceiling and glides down gently instead |
| Ceiling | Y=1000 | Auto flight never takes you above this |
| Glide down | 60 blocks | How far it descends from the ceiling before climbing again |
| Start below ceiling | 40 blocks | How far under the ceiling that descent begins |
| Glide angle | 5 degrees | How steeply it glides down (5 degrees is about 3 blocks/s) |
| Durability warning | 30 left | When to warn that the elytra is nearly worn out; 0 is off |
| HUD / Altitude graph | On | The readout shown while gliding |
| Arda Anarchy preset | | Speed limit on at 39.5 m/s and ceiling on at Y=1000 |
| Reset to defaults | | Back to a fresh install |

The two flight cycles:

- **Fast** (default): the original mod's cycle. Dives to about 47 m/s, climbs about
  0.9 blocks/s, about 23 blocks/s forward.
- **Speed-limited**: used when the speed limit is on and below 50 m/s. Tuned for a 39.5 m/s
  limit: peaks near 36 m/s, climbs about 0.5 blocks/s, about 18 blocks/s forward.

With either one, if a dive can't reach speed (something is holding you back) it gives up after
15 seconds and glides for 5 before trying again, instead of holding the nose down.

The cycles' raw numbers (angles, switch-over speeds, turn rates) are only in the config file.
They are tuned as a set; changing one can make the cycle lose height.

## Building and testing

    gradlew build

The jar lands in `build/libs`. The build runs unit tests that fly both cycles through a copy
of the vanilla glide formula.

    gradlew runClientGameTest

boots a real client, glides a survival player and checks the same things in the game itself
(about six minutes; it opens a game window).

## Credits and license

A Fabric 26.2 continuation of [AutoElytraFlight](https://github.com/Cheaterpaul/AutoElytraFlight)
by Cheaterpaul, itself a fork of simonlourson's original Elytra Auto Flight. GPL-3.0, see `LICENSE`.
