components {
  id: "script"
  component: "/duckhunt/scripts/guy.script"
}
embedded_components {
  id: "sprite"
  type: "sprite"
  data: "default_animation: \"Idle\"\n"
  "material: \"/builtins/materials/sprite.material\"\n"
  "textures {\n"
  "  sampler: \"texture_sampler\"\n"
  "  texture: \"/duckhunt/atlases/sprites.atlas\"\n"
  "}\n"
  ""
  position {
    z: 0.7
  }
}
embedded_components {
  id: "projectile_factory"
  type: "factory"
  data: "prototype: \"/duckhunt/gameobjects/projectile.go\"\n"
  ""
}

