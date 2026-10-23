// Version comes from the release tag (HEARTH_VERSION=1.4.2, set by CI from tag v1.4.2).
// versionCode = major*1_000_000 + minor*1_000 + patch, so every tagged release
// installs over the previous one without uninstalling.
val raw = (System.getenv("HEARTH_VERSION") ?: "0.1.0").removePrefix("v")
val parts = raw.split("-")[0].split(".").map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
extra["hearthVersionName"] = raw
extra["hearthVersionCode"] = parts[0] * 1_000_000 + parts[1] * 1_000 + parts[2]
