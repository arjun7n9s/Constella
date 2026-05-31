# Constella

Here's why I exist. Braille is a doorway, but not everyone holding the page can walk through it. A sighted parent gets a note home from their blind child's school and can't read it. A caregiver finds a label, a card, a page of dots and has no idea what it says. Someone who recently lost their sight is still learning Braille, slowly, and wants to check whether they got it right. In all of these moments, the words are right there under your fingers, and they stay locked away.

I'm here to open that door. Point your camera at a Braille page and I'll find the raised dots, make sense of them, and speak the words out loud. That's it. No fluency required, no waiting, no guessing.

The name comes from the way Braille works: scattered points, like stars, that only mean something once you connect them. That's what I do, I connect the dots and hand you the meaning.

And I do all of it on your device. Your pages never leave your phone, and I work even with no signal, because the moments you'll need me, a kitchen, a classroom, a doctor's office, aren't always moments with good internet.

## Who I'm for

- Sighted parents, teachers, and caregivers who never learned Braille but live alongside it every day.
- People who are newly blind or still learning Braille, who want to hear a page and check their reading.
- Anyone who meets a page of dots and needs to know what it says, out loud, right now.

## How I work

Aim your camera at a Braille page and I light it, read the raised dots, and speak them back to you. Everything runs on your phone:

- **On-device, always.** Image processing, dot detection, translation, and speech all happen locally.
- **Offline by default.** No signal needed. Your pages never leave your hands.
- **Embossed and handwritten.** Tuned for printed Braille, with a mode for handwritten dots too.
- **Grade 1 and Grade 2.** Automatic grade detection with a one-tap override.

## Tech

Native Android (Kotlin + Jetpack Compose), CameraX, OpenCV, TensorFlow Lite, liblouis, and Android TextToSpeech.

## Status

Early build. The specification lives in [`.kiro/specs/braille-scanner`](.kiro/specs/braille-scanner) (requirements, design, and the implementation plan).
