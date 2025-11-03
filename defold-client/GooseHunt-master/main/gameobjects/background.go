components {
  id: "background1"
  component: "/main/scripts/background.script"
}
embedded_components {
  id: "background"
  type: "sprite"
  data: "default_animation: \"Background\"\n"
  "material: \"/builtins/materials/sprite.material\"\n"
  "textures {\n"
  "  sampler: \"texture_sampler\"\n"
  "  texture: \"/main/atlases/environment.atlas\"\n"
  "}\n"
  ""
}
