# Gortrans

Android application which tracks location of public transport vehicles in Novosibirsk city (Russia). It works by using the same API [nskgortrans](https://maps.nskgortrans.ru) site uses internally.

## Building

As a temporary workaround, you'll need to install hotfix version of [gradle-android-scala-plugin](https://github.com/AllBus/gradle-android-scala-plugin) to local Maven repository:

1. git clone https://github.com/jbreathe/gradle-android-scala-plugin.git
2. git checkout v3.4.0-hf
3. gradlew install

And then build app itself:

1. Install Android SDK platform (API level 28)
2. Replace 'API_KEY' in string 'google_maps_key' in 'strings' resource with your Google Maps API key
3. Run ```gradlew :app:packageDebug```
