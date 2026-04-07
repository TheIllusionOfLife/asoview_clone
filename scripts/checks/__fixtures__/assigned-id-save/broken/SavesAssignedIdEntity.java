// Broken fixture: assigned-@Id entity is saved via .save(new Foo(...)) which
// routes through merge() and silently succeeds on retries.
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
class Foo {
  @Id Long id;
}

class FooCaller {
  void run(FooRepo repo) {
    repo.save(new Foo());
  }
}

interface FooRepo {
  Foo save(Foo f);
}
