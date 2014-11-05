void
showTreeNodes(Block* block, int id, char* bp, int depth)
{
  SpanningTree* st = block->trees[id];
  sprintf(bp, "%*s%2d:%d %s%s", depth, " ", block->id, st->value, kind2str(st->kind), st->kind == Leaf ? "\n" : " -> ");
  if (st->kind == Leaf) return;
  bp += strlen(bp);
  int i;
  for (i=0; i<NUM_PORTS; i++) {
    if (st->neighbors[i] == Child) {
      sprintf(bp, " %2d", port2id(block, i));
      bp += strlen(bp);
    }
  }
  strcat(bp, "\n");
  bp += strlen(bp);
  for (i=0; i<NUM_PORTS; i++) {
    if (st->neighbors[i] == Child) {
      Block* cb = port2block(block, i);
      showTreeNodes(cb, id, bp, depth+1);
      bp += strlen(bp);
    }
  }
}

