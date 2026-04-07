// Clean fixture: an assigned-@Id entity is only ever saved via insertIfMissing.
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
class Foo {
  @Id Long id;
}

class FooCaller {
  void run(FooRepo repo) {
    repo.insertIfMissing(1L);
    repo.saveAndFlush(new Foo());
  }
}

interface FooRepo {
  int insertIfMissing(long id);
  Foo saveAndFlush(Foo f);
}
