#include <polyglot.h>
#include <truffle.h>

int main() {
  void *p = polyglot_import("object");
  
  void *p1 = truffle_handle_for_managed(p);
  
  truffle_release_handle(p1);

  return 0;
}
