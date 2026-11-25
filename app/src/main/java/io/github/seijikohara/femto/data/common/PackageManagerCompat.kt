package io.github.seijikohara.femto.data.common

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/*
 * Android 10-12 fallbacks for the PackageManager overloads that take the
 * API 33 `*Flags` wrapper classes. Below API 33 those classes do not exist,
 * so calling the new overloads throws NoClassDefFoundError; the deprecated
 * int overloads behave identically there.
 */

@Suppress("DEPRECATION")
internal fun PackageManager.resolveActivityCompat(
    intent: Intent,
    flags: Int = 0,
): ResolveInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        resolveActivity(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    } else {
        resolveActivity(intent, flags)
    }

@Suppress("DEPRECATION")
internal fun PackageManager.queryIntentActivitiesCompat(
    intent: Intent,
    flags: Int = 0,
): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    } else {
        queryIntentActivities(intent, flags)
    }

@Suppress("DEPRECATION")
internal fun PackageManager.queryIntentServicesCompat(
    intent: Intent,
    flags: Int = 0,
): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    } else {
        queryIntentServices(intent, flags)
    }

@Suppress("DEPRECATION")
internal fun PackageManager.getApplicationInfoCompat(
    packageName: String,
    flags: Int = 0,
): ApplicationInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
    } else {
        getApplicationInfo(packageName, flags)
    }

@Suppress("DEPRECATION")
internal fun PackageManager.getPackageInfoCompat(
    packageName: String,
    flags: Int = 0,
): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        getPackageInfo(packageName, flags)
    }
