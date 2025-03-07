plugins {
    id("buildroot.orchestration")
    id("buildroot.versioning")
}

orchestration {
    participatingIncludedBuilds.set(listOf("core", "gradle-plugins"))
}
