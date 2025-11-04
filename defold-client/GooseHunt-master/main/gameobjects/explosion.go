components {
  id: "explosion"
  component: "/GooseHunt-master/main/scripts/explosion.script"
}
embedded_components {
  id: "sprite"
  type: "sprite"
  data: "default_animation: \"explode\"\n"
  "material: \"/builtins/materials/sprite.material\"\n"
  "textures {\n"
  "  sampler: \"texture_sampler\"\n"
  "  texture: \"/GooseHunt-master/main/atlases/sprites.atlas\"\n"
  "}\n"
  ""
  scale {
    x: 0.5
    y: 0.5
    z: 1.0E-6
  }
}
