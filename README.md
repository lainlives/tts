> [!IMPORTANT]
> Google has announced that, starting in 2026/2027, all apps on certified Android devices
> will require the developer to submit personal identity details directly to Google.
> Since the developers of this app do not agree to this requirement, this app will no longer 
> work on certified Android devices after that time.

# Single voice fork of woheller69's sherpa-tts
SherpaTTS is an Android Text-to-Speech engine based on Next-gen Kaldi.

Put a `model.onnx` and `tokens.txt` in the `app/src/main/assets/model/engUSA` directory (or, whichever is relevant for your language). I haven't tested but I do not think I broke multi language. You will have to edit the `MainActivity.kt` though to update directory names. No configuration options in app, its purpose is to just serve as a preconfigured system TTS app.

## Sideloading other models.... I do not think I broke this.

You can install other Piper models via adb. Depending on your Android version it may also work via "normal" USB connection.
Create `modelDir` in `sdcard/Android/data/org.ll.ttsenterprise/files` and put 3 files there:

* `model.onnx`
* `tokens.txt`
* `lang` which in the 1st line contains the 3 letter code e.g. `eng` and in the 2nd line contains the model name

At next start the app will migrate it to the new directory structure and add it to installed languages.

# License
This work is licensed under GPLv3 license, © woheller69

- This app is based on the [Sherpa ONNX Project](https://github.com/k2-fsa/sherpa-onnx), published under Apache-2.0 license
- It uses data from [eSpeak NG](https://github.com/espeak-ng/espeak-ng), published under GPLv3 license
- At first start it downloads and installs a Piper or Coqui voice model from Hugging Face. 

## If you are going to donate may as well be to the guy who made the original app.

Send a coffee to `woheller69@t-online.de`

[![PayPal](https://www.paypalobjects.com/webstatic/de_DE/i/de-pp-logo-150px.png)](https://www.paypal.com/signin)

Or via this link (with fees):
[![Donate](https://img.shields.io/badge/Donate%20with%20Debit%20or%20Credit%20Card-002991?style=plastic)](https://www.paypal.com/donate?hosted_button_id=XVXQ54LBLZ4AA)
