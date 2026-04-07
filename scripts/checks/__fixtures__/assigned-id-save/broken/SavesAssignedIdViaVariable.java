// Broken fixture: assigned-@Id entity saved via a local variable.
// The inline-only grep would miss this; the variable-form pass must catch it.
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
class Bar {
  @Id Long id;
}

class BarCaller {
  void run(BarRepo repo) {
    Bar b = new Bar();
    repo.save(b);
  }
}

interface BarRepo {
  Bar save(Bar b);
}
