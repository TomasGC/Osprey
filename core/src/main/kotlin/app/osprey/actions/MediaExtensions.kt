package app.osprey.actions

/** Extensions this tool treats as still images, lowercase — matches the POC's `IMAGE_EXT`. */
val imageExtensions = setOf(".jpg", ".jpeg", ".png", ".heic")

/** Extensions this tool treats as video, lowercase — matches the POC's `VIDEO_EXT`. */
val videoExtensions = setOf(".mp4", ".mov", ".avi", ".3gp", ".m4v", ".mkv")
