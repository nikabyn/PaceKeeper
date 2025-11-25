package org.htwk.pacing.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

fun currentExtendedColors(darkTheme: Boolean) =
    if (darkTheme) ExtendedColors(
        red = ColorPalette.red80,
        orange = ColorPalette.orange80,
        yellow = ColorPalette.yellow80,
        green = ColorPalette.green80,
        cyan = ColorPalette.cyan80,
        blue = ColorPalette.blue80,
    ) else ExtendedColors(
        red = ColorPalette.red60,
        orange = ColorPalette.orange60,
        yellow = ColorPalette.yellow60,
        green = ColorPalette.green60,
        cyan = ColorPalette.cyan60,
        blue = ColorPalette.blue60,
    )

@Immutable
data class ExtendedColors(
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val green: Color,
    val cyan: Color,
    val blue: Color,
)

internal val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        red = Color.Unspecified,
        orange = Color.Unspecified,
        yellow = Color.Unspecified,
        green = Color.Unspecified,
        cyan = Color.Unspecified,
        blue = Color.Unspecified,
    )
}

object ColorPalette {
    val red99 = Color(0xfff8dcdc)
    val red95 = Color(0xfff2b6b6)
    val red90 = Color(0xffef8f8f)
    val red80 = Color(0xffed6868)
    val red70 = Color(0xffea4040)
    val red60 = Color(0xffe61919)
    val red50 = Color(0xffbd1717)
    val red40 = Color(0xff941616)
    val red30 = Color(0xff6b1515)
    val red20 = Color(0xff431212)
    val red10 = Color(0xff1e0c0c)

    val orange99 = Color(0xfffbe3d9)
    val orange95 = Color(0xfff9c4b0)
    val orange90 = Color(0xfffaa685)
    val orange80 = Color(0xfffb8759)
    val orange70 = Color(0xfffd672d)
    val orange60 = Color(0xfffc4903)
    val orange50 = Color(0xffd03e05)
    val orange40 = Color(0xffa23308)
    val orange30 = Color(0xff74290b)
    val orange20 = Color(0xff481e0d)
    val orange10 = Color(0xff20100a)

    val yellow99 = Color(0xfffbf2d9)
    val yellow95 = Color(0xfff9e7b0)
    val yellow90 = Color(0xfffadd85)
    val yellow80 = Color(0xfffbd359)
    val yellow70 = Color(0xfffdc92d)
    val yellow60 = Color(0xfffcbe03)
    val yellow50 = Color(0xffd09d05)
    val yellow40 = Color(0xffa27b08)
    val yellow30 = Color(0xff745a0b)
    val yellow20 = Color(0xff483a0d)
    val yellow10 = Color(0xff201b0a)

    val green99 = Color(0xffebf7dd)
    val green95 = Color(0xffd7f1b8)
    val green90 = Color(0xffc4ec92)
    val green80 = Color(0xffb1e96b)
    val green70 = Color(0xff9de544)
    val green60 = Color(0xff8ae01f)
    val green50 = Color(0xff73b91c)
    val green40 = Color(0xff5b901a)
    val green30 = Color(0xff446817)
    val green20 = Color(0xff2d4214)
    val green10 = Color(0xff161d0d)

    val cyan99 = Color(0xffddf7f0)
    val cyan95 = Color(0xffb8f0e3)
    val cyan90 = Color(0xff93ecd6)
    val cyan80 = Color(0xff6ce8c9)
    val cyan70 = Color(0xff6ce8c9)
    val cyan60 = Color(0xff45e4bd)
    val cyan50 = Color(0xff1db891)
    val cyan40 = Color(0xff1a9072)
    val cyan30 = Color(0xff186854)
    val cyan20 = Color(0xff144136)
    val cyan10 = Color(0xff0d1d19)

    val blue99 = Color(0xffddfcff)
    val blue95 = Color(0xffcff0ff)
    val blue90 = Color(0xffbde2ff)
    val blue80 = Color(0xff9ac6ff)
    val blue70 = Color(0xff77abff)
    val blue60 = Color(0xff5491ea)
    val blue50 = Color(0xff3078ce)
    val blue40 = Color(0xff00488e)
    val blue30 = Color(0xff00316b)
    val blue20 = Color(0xff052a57)
    val blue10 = Color(0xff051429)
}