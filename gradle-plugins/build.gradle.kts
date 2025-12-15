plugins {
    id("buildroot.orchestration")
    id("buildroot.versioning")
}

tasks.register("publishPlugins") {
    dependsOn(":graphdsl-plugin:publishPlugins")
}
