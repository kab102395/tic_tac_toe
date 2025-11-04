components {
  id: "goose"
  component: "/GooseHunt-master/main/scripts/goose.script"
}
embedded_components {
  id: "sprite"
  type: "sprite"
  data: "default_animation: \"flap\"\n"
  "material: \"/builtins/materials/sprite.material\"\n"
  "textures {\n"
  "  sampler: \"texture_sampler\"\n"
  "  texture: \"/GooseHunt-master/main/atlases/sprites.atlas\"\n"
  "}\n"
  ""
  position {
    z: 0.5
  }
}
