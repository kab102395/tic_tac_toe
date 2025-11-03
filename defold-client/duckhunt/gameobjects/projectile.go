components {
  id: "projectile"
  component: "/duckhunt/scripts/projectile.script"
}
embedded_components {
  id: "sprite"
  type: "sprite"
  data: "default_animation: \"rock\"\n"
  "material: \"/builtins/materials/sprite.material\"\n"
  "textures {\n"
  "  sampler: \"texture_sampler\"\n"
  "  texture: \"/duckhunt/atlases/sprites.atlas\"\n"
  "}\n"
  ""
  position {
    x: 2.0
    y: -27.0
  }
}

