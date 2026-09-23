plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.8.9-forge"

stonecutter parameters {
    val (_, loader) = current.project.split('-', limit = 2)
    constants { match(loader, "forge", "legacyfabric") }
    swaps["minecraft"] = "\"${current.version}\";"
}
