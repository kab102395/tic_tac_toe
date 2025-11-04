components {
  id: "script"
  component: "/GooseHunt-master/main/scripts/guy.script"
}
embedded_components {
  id: "sprite"
  type: "sprite"
  data: "default_animation: \"Idle\"\n"
  "material: \"/builtins/materials/sprite.material\"\n"
  "textures {\n"
  "  sampler: \"texture_sampler\"\n"
  "  texture: \"/GooseHunt-master/main/atlases/sprites.atlas\"\n"
  "}\n"
  ""
  position {
    z: 0.7
  }
}
embedded_components {
  id: "projectile_factory"
  type: "factory"
  data: "prototype: \"/GooseHunt-master/main/gameobjects/projectile.go\"\n"
  ""
}
