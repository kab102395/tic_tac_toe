components {
  id: "gui"
  component: "/main/launcher.gui"
}
components {
  id: "controller"
  component: "/main/launcher_controller.script"
}
embedded_components {
  id: "factory_ttt"
  type: "collectionfactory"
  data: "prototype: \"/ttt/ttt_game.collection\"\n"
  ""
}
