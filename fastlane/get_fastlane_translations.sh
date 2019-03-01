#!/bin/bash

test -d metadata/android/fr-FR || mkdir metadata/android/fr-FR
cp ../app/src/main/res/fr/phonetrack_android_fastlane/full_description.txt metadata/android/fr-FR/full_description.txt
cp ../app/src/main/res/fr/phonetrack_android_fastlane/short_description.txt metadata/android/fr-FR/short_description.txt
echo "PhoneTrack" > metadata/android/fr-FR/title.txt

test -d metadata/android/de-DE || mkdir metadata/android/de-DE
cp ../app/src/main/res/de/phonetrack_android_fastlane/full_description.txt metadata/android/de-DE/full_description.txt
cp ../app/src/main/res/de/phonetrack_android_fastlane/short_description.txt metadata/android/de-DE/short_description.txt
echo "PhoneTrack" > metadata/android/de-DE/title.txt

test -d metadata/android/es-ES || mkdir metadata/android/es-ES
cp ../app/src/main/res/es-ES/phonetrack_android_fastlane/full_description.txt metadata/android/es-ES/full_description.txt
cp ../app/src/main/res/es-ES/phonetrack_android_fastlane/short_description.txt metadata/android/es-ES/short_description.txt
echo "PhoneTrack" > metadata/android/es-ES/title.txt

test -d metadata/android/el-GR || mkdir metadata/android/el-GR
cp ../app/src/main/res/el/phonetrack_android_fastlane/full_description.txt metadata/android/el-GR/full_description.txt
cp ../app/src/main/res/el/phonetrack_android_fastlane/short_description.txt metadata/android/el-GR/short_description.txt
echo "PhoneTrack" > metadata/android/el-GR/title.txt

test -d metadata/android/it-IT || mkdir metadata/android/it-IT
cp ../app/src/main/res/it/phonetrack_android_fastlane/full_description.txt metadata/android/it-IT/full_description.txt
cp ../app/src/main/res/it/phonetrack_android_fastlane/short_description.txt metadata/android/it-IT/short_description.txt
echo "PhoneTrack" > metadata/android/it-IT/title.txt

test -d metadata/android/nl-NL || mkdir metadata/android/nl-NL
cp ../app/src/main/res/nl/phonetrack_android_fastlane/full_description.txt metadata/android/nl-NL/full_description.txt
cp ../app/src/main/res/nl/phonetrack_android_fastlane/short_description.txt metadata/android/nl-NL/short_description.txt
echo "PhoneTrack" > metadata/android/nl-NL/title.txt

test -d metadata/android/pl-PL || mkdir metadata/android/pl-PL
cp ../app/src/main/res/pl/phonetrack_android_fastlane/full_description.txt metadata/android/pl-PL/full_description.txt
cp ../app/src/main/res/pl/phonetrack_android_fastlane/short_description.txt metadata/android/pl-PL/short_description.txt
echo "PhoneTrack" > metadata/android/pl-PL/title.txt

test -d metadata/android/ro-RO || mkdir metadata/android/ro-RO
cp ../app/src/main/res/ro/phonetrack_android_fastlane/full_description.txt metadata/android/ro-RO/full_description.txt
cp ../app/src/main/res/ro/phonetrack_android_fastlane/short_description.txt metadata/android/ro-RO/short_description.txt
echo "PhoneTrack" > metadata/android/ro-RO/title.txt

test -d metadata/android/ru-RU || mkdir metadata/android/ru-RU
cp ../app/src/main/res/ru/phonetrack_android_fastlane/full_description.txt metadata/android/ru-RU/full_description.txt
cp ../app/src/main/res/ru/phonetrack_android_fastlane/short_description.txt metadata/android/ru-RU/short_description.txt
echo "PhoneTrack" > metadata/android/ru-RU/title.txt

git add .
