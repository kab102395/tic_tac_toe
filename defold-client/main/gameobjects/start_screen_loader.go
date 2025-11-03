-- Start screen loader gameobject
-- Loads and manages the start_screen collection

components {
  id: "script"
  component: "/main/scripts/start_screen_loader.script"
}
collectionfactories {
  id: "factory"
  prototype: "/main/gameobjects/start_screen.factory"
}
