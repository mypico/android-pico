# android-pico ReadMe

The Pico project is liberating humanity from passwords. See https://www.mypico.org.

android-pico is a version of the Pico app that runs on Android. Use it instead of all your horrid passwords.

## Documentation

For details on the internal classes and structure of the code, see:

https://docs.mypico.org/developer/android-pico/

## Install

Start by ensuring you've downloaded the latest version from the git repository and are inside the project folder.
```
git clone git@github.com:mypico/android-pico.git
cd android-pico
```

The Pico java library jpico is needed, but has been included as a submodule. You can set it up by running the following commands inside the root android-pico folder.

```
git submodule init
git submodule update
```

The easiest way to then build android-pico is using Android Studio, by importing the gradle scripts.

If you want to build from the command line, you'll need the JDK and ant to build the code. On Ubuntu, you can install what you need using the following commands:

```
sudo apt install openjdk-8-jdk ant lib32stdc++6 lib32z1
```

You'll also need the Android SDK. You can download this using the following commands (you'll need to accept the Android SDK licence agreement during this process). Again, do this inside the root android-pico folder.

```
wget --output-document=android-sdk.tgz https://dl.google.com/android/android-sdk_r24.4.1-linux.tgz
tar --extract --gzip --file=android-sdk.tgz
android-sdk-linux/tools/android update sdk --no-ui --all --filter platform-tools,tools,build-tools-25.0.2,android-23,extra-android-m2repository,extra-google-m2repository
export ANDROID_HOME=$PWD/android-sdk-linux 
export JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF8

```

You can then build like this.
```
./gradlew assembleDebug
./gradlew javadoc
```

This will leave an apk at `android-pico/android-pico/build/outputs/apk/android-pico-debug.apk` and documentation files in the `android-pico/android-pico/build/docs` folder.

The easiest way to install the app is to deploy it to your phone via USB. To check whether your phone is developer-enabled and correctly connected, enter the following. 

```
adb devices -l
```

If you don't see your device showing correctly in this list, you'll need to fix this first. In this case check out the details on the [Android developer site](https://developer.android.com/studio/command-line/adb.html).

Once your developer-enabled Android phone is correctly connected to your computer via USB you can install the app with the following.
```
adb -d install android-pico/build/outputs/apk/android-pico-debug.apk
```

## License

android-pico is released under the AGPL licence. Read COPYING for information.

There is an older BSD-licenced version of the code available at https://github.com/mypico/android-pico-bsd

## Contributing

We welcome comments and contributions to the project. If you're interested in contributing please see here: https://get.mypico.org/cla/

## Contact and Links

More information can be found at: http://mypico.org

The Pico project team:
 * Frank Stajano (PI), Frank.Stajano@cl.cam.ac.uk
 * David Llewellyn-Jones, David.Llewellyn-Jones@cl.cam.ac.uk
 * Claudio Dettoni, cd611@cam.ac.uk
 * Seb Aebischer, seb.aebischer@cl.cam.ac.uk
 * Kat Krol, kat.krol@cl.cam.ac.uk
 * David Harrison, David.Harrison@cl.cam.ac.uk


