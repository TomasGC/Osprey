package app.osprey.actions

/** Extensions of HEIC photos: a container that keeps its rotation in an `irot` property, not in a tag. */
val heicExtensions = setOf(".heic", ".heif")

/** Extensions this tool treats as still images, lowercase — matches the POC's `IMAGE_EXT`. */
val imageExtensions = setOf(".jpg", ".jpeg", ".png") + heicExtensions

/** Extensions this tool treats as video, lowercase — matches the POC's `VIDEO_EXT`. */
val videoExtensions = setOf(".mp4", ".mov", ".avi", ".3gp", ".m4v", ".mkv")
