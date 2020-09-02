Besides allowing you to upload skins to a custom skin server, HDSkins also includes resourcepack support for loading _local_ skins!

Simply create a resourcepack and add a json file like this similar to this one:

```
{
  "skins": [
      {
         "type": "SKIN",
         "model": "default",
         "name": "Sollace",
         "skin": "hdskins:textures/skins/super_silly_pony.png"
      }
  ]
}
```
And place it at `assets/hdskins/textures/skins/skins.json`

Name is the display name of the user who's skin you want to replace. You can also specify a uuid of the player's minecraft account:

```
{
  "skins": [
      {
         "type": "SKIN",
         "model": "default",
         "uuid": "0000-0000-0000-0000",
         "skin": "hdskins:textures/skins/super_silly_pony.png"
      }
  ]
}
```

- Type is type of skin which can be any of (`SKIN`, `ELYTRA`, or `CAPE`), 
- Skin is the resource path for the texture you want to use, also in the resourcepack.
- Model is the vanilla model type, either `DEFAULT` or `ALEX` which controls which arm types the player will have

In this case the json file will change me (Sollace) to use the skin texture located at `/assets/hdskins/textures/skins/super_silly_pony.png`.


If you wish to match more than one user, there is also support for using regular expressions.

```{
   "skins": [
       {
          "type": "SKIN",
          "model": "default",
          "pattern": "S[o]+llace|Notch",
          "skin": "hdskins:textures/skins/super_silly_pony.png"
       }
   ]
 }```