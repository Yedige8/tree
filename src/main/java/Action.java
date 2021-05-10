import entity.Tree;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.List;
import java.util.Scanner;

public class Action {

    private static final EntityManagerFactory FACTORY;

    static {
        FACTORY = Persistence.createEntityManagerFactory("default");
    }

    private static final Scanner IN = new Scanner(System.in);

    public static void menu() {
        EntityManager manager = FACTORY.createEntityManager();
        List<Tree> trees = manager
                .createQuery("""
                        select c
                        from Tree c
                        order by c.left
                        """, Tree.class)
                .getResultList();
        for (Tree tree : trees) {
            System.out.println(
                    ("- ".repeat(Math.max(0, tree.getLevel())) + tree.getName() + " [" + tree.getId() + "]")
            );
        }
        System.out.println("""
                Создать категорию [1]
                Редактровать категорию [2]
                Удалить категорию [3]""");
        System.out.print("Выберите что нужно сделать: ");
        String action = IN.nextLine();
        switch (action) {
            case "1" -> create();
            case "2" -> update();
            case "3" -> remove();
            default -> System.out.println("Такого действя не существует");
        }
    }

    public static void create() {
        EntityManager manager = FACTORY.createEntityManager();
        manager.getTransaction().begin();
        System.out.print("Введите ID или 0 для создания нового родителя: ");
        long parentId = Long.parseLong(IN.nextLine());
        System.out.print("Введите ИМЯ: ");
        String name = IN.nextLine();
        if (parentId == 0) {
            int maxRight = manager
                    .createQuery("""
                            select max(v.right)
                            from Tree v
                            """, Integer.class)
                    .getSingleResult();
            Tree tree = new Tree();
            tree.setName(name);
            tree.setLeft(maxRight + 1);
            tree.setRight(maxRight + 2);
            tree.setLevel(1);
            manager.persist(tree);
        } else {
            Tree parent = manager.find(Tree.class, parentId);
            manager
                    .createQuery("""
                            update Tree v
                            set v.left = v.left + 2
                            where v.left > :parent_right
                            """)
                    .setParameter("parent_right", parent.getRight())
                    .executeUpdate();
            manager
                    .createQuery("""
                            update Tree v
                            set v.right = v.right + 2
                            where v.right >= :parent_right
                            """)
                    .setParameter("parent_right", parent.getRight())
                    .executeUpdate();
            Tree tree = new Tree();
            tree.setName(name);
            tree.setLeft(parent.getRight());
            tree.setRight(parent.getRight() + 1);
            tree.setLevel(parent.getLevel() + 1);
            manager.persist(tree);
        }
        manager.getTransaction().commit();
    }

    public static void update() {
        EntityManager manager = FACTORY.createEntityManager();
        manager.getTransaction().begin();
        System.out.print("Введите ID категории которую нужно перенести: ");
        long moveId = Long.parseLong(IN.nextLine());
        Tree moveTree = manager.find(Tree.class, moveId);
        System.out.print("Введите ID категорию в которую нужно перенести: ");
        long parentId = Long.parseLong(IN.nextLine());
        manager
                .createQuery("""
                        update Tree t
                        set t.left = 0 - t.left,
                            t.right = 0 - t.right
                        where t.left >= :move_left and
                              t.right <= :move_right
                        """)
                .setParameter("move_right", moveTree.getRight())
                .setParameter("move_left", moveTree.getLeft())
                .executeUpdate();
        manager
                .createQuery("""
                        update Tree t
                        set t.left = t.left - :move_size
                        where t.left > :move_right
                        """)
                .setParameter("move_size", moveTree.getRight() - moveTree.getLeft() + 1)
                .setParameter("move_right", moveTree.getRight())
                .executeUpdate();
        manager
                .createQuery("""
                        update Tree t
                        set t.right = t.right - :move_size
                        where t.right > :move_right
                        """)
                .setParameter("move_size", moveTree.getRight() - moveTree.getLeft() + 1)
                .setParameter("move_right", moveTree.getRight())
                .executeUpdate();
        if (parentId == 0) {
            int maxRight = manager
                    .createQuery("""
                            select max(t.right)
                            from Tree t
                            """, Integer.class)
                    .getSingleResult();
            manager
                    .createQuery("""
                            update Tree t
                            set t.left = 0 - t.left + :parameter,
                                t.right = 0- t.right + :parameter,
                                t.level =  t.level - :move_level + 1
                            where t.left <= 0 - :move_left and
                                  t.right >= 0 - :move_right
                            """)
                    .setParameter("parameter", maxRight - moveTree.getRight()+2)
                    .setParameter("move_level", moveTree.getLevel())
                    .setParameter("move_left", moveTree.getLeft())
                    .setParameter("move_right", moveTree.getRight())
                    .executeUpdate();
        } else {
            Tree parentTree = manager.find(Tree.class, parentId);
            int newAreaKey;
            if (parentTree.getRight() > moveTree.getRight()) {
                newAreaKey = parentTree.getRight() - (moveTree.getRight() - moveTree.getLeft() + 1);
            } else {
                newAreaKey = parentTree.getRight();
            }
            manager
                    .createQuery("""
                            update Tree t
                            set t.left = t.left + :move_tree_size
                            where t.left >= :new_area_key
                            """)
                    .setParameter("move_tree_size", moveTree.getRight() - moveTree.getLeft() + 1)
                    .setParameter("new_area_key", newAreaKey)
                    .executeUpdate();
            manager
                    .createQuery("""                    
                            update Tree t
                            set t.right = t.right + :move_tree_size
                            where t.right >= :new_area_key
                            """)
                    .setParameter("move_tree_size", moveTree.getRight() - moveTree.getLeft() + 1)
                    .setParameter("new_area_key", newAreaKey)
                    .executeUpdate();

            int parameter;
            if (parentTree.getRight() > moveTree.getRight()) {
                parameter = parentTree.getRight() - moveTree.getRight() - 1;
            } else {
                parameter = parentTree.getRight() - moveTree.getRight() + (moveTree.getRight() - moveTree.getLeft());
            }
            manager
                    .createQuery("""
                            update Tree t
                             set t.left = 0 - t.left + :parameter,
                                 t.right = 0 - t.right + :parameter,
                                 t.level = t.level - :level_diff +1
                            where t.right >= :move_right
                            and t.left <= :move_left
                            """)
                    .setParameter("parameter", parameter)
                    .setParameter("level_diff", moveTree.getLevel() - parentTree.getLevel())
                    .setParameter("move_right", -moveTree.getRight())
                    .setParameter("move_left", -moveTree.getLeft())
                    .executeUpdate();
        }
        manager.getTransaction().commit();
    }

    public static void remove() {
        EntityManager manager = FACTORY.createEntityManager();
        manager.getTransaction().begin();
        System.out.print("Введите ID категории для удаления: ");
        long parentId = Long.parseLong(IN.nextLine());
        Tree parentTree = manager.find(Tree.class, parentId);
        manager
                .createQuery("""
                        delete from Tree t
                        where t.left >= :parent_left
                        and t.right <=:parent_right
                        """)
                .setParameter("parent_left", parentTree.getLeft())
                .setParameter("parent_right", parentTree.getRight())
                .executeUpdate();
        manager
                .createQuery("""
                        update Tree t
                        set t.left = t.left - :parent_size
                        where t.left > :parent_right
                        """)
                .setParameter("parent_size", parentTree.getRight() - parentTree.getLeft() + 1)
                .setParameter("parent_right", parentTree.getRight())
                .executeUpdate();
        manager
                .createQuery("""
                        update Tree t
                        set t.right = t.right - :parent_size
                        where t.right >= :parent_right
                        """)
                .setParameter("parent_size", parentTree.getRight() - parentTree.getLeft() + 1)
                .setParameter("parent_right", parentTree.getRight())
                .executeUpdate();
        manager.getTransaction().commit();
    }
}
