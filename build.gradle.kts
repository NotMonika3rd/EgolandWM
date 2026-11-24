plugins {
    kotlin("multiplatform") version "2.4.21"
}

group = "reverie.egoland"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    linuxX64 {
        binaries {
            executable {
                entryPoint = "reverie.egoland.main"
                runTaskProvider?.configure {
                    environment("WLR_BACKENDS","wayland")
                }
            }
        }

        compilations.getByName("main") {
            cinterops {
                create("wlroots") {
                    defFile(project.file("src/linuxMain/cinterop/wlroots.def"))
                    packageName("wlr")
                    extraOpts(
                        "-Xccall-mode", "indirect",
                        "-Xcompile-source",
                        project.file("src/linuxMain/cinterop/wlr_helpers.c").absolutePath,
                        "-Xsource-compiler-option", "-x",
                        "-Xsource-compiler-option", "c",
                        "-Xsource-compiler-option", "-DWLR_USE_UNSTABLE",
                        "-Xsource-compiler-option", "-DWLR_PRIVATE=",
                        "-Xsource-compiler-option", "-I/usr/include",
                        "-Xsource-compiler-option", "-I/usr/include/wlroots-0.20",
                        "-Xsource-compiler-option", "-I/usr/include/pixman-1",
                        "-Xsource-compiler-option", "-I/usr/include/libdrm",
                        "-Xsource-compiler-option", "-I${project.file("src/linuxMain/cinterop/").absolutePath}"
                    )
                    compilerOpts(
                        "-DWLR_USE_UNSTABLE",
                        "-DWLR_PRIVATE=",
                        "-std=gnu11",
                        "-I${project.file("src/linuxMain/cinterop/").absolutePath}",
                        "-I/usr/include/wlroots-0.20",
                        "-I/usr/include/pixman-1",
                        "-I/usr/include/libdrm"
                    )
                }
            }
        }
    }
}
