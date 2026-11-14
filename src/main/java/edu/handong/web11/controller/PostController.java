package edu.handong.web11.controller;

import edu.handong.web11.domain.Post;
import edu.handong.web11.dto.PostForm;
import edu.handong.web11.service.PostService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
public class PostController {
    private final PostService posts;

    public PostController(PostService posts) {
        this.posts = posts;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/posts";
    }

    @GetMapping("/posts")
    public String list(Model model) {
        model.addAttribute("posts", posts.all());
        return "posts/list";
    }

    /*
     [ 게시글 작성 폼 요청 ]
     - 빈 PostForm을 모델에 담아 posts/form 템플릿에 전달한다.
     - 폼은 method="post", enctype="multipart/form-data", <input type="file" name="files" multiple>로 구성된다.
    */
    @GetMapping("/posts/new")
    public String form(Model model) {
        model.addAttribute("postForm", new PostForm());
        return "posts/form";
    }

    /*
     [ 게시글 작성 + 파일 업로드 요청 처리 ]
     - 요청은 multipart/form-data로 전송되며, title, content, files가 각각 Part로 나뉘어 한 요청에 담겨 온다.
     - title, content → @ModelAttribute가 PostForm 객체 하나로 묶어서 바인딩하고, @Valid로 입력값을 검증한다.
     - files → @RequestParam("files")로 따로 받고, 여러개의 파일을 첨부할 수 있으므로 List<MultipartFile>을 사용한다.
     - Controller는 요청 수신, 검증 결과 확인, 응답(redirect)만 담당하고 저장 로직은 Service와 Repository가 실행한다.
     */
    @PostMapping("/posts")
    public String create(@Valid @ModelAttribute("postForm") PostForm form, BindingResult errors,
                         @RequestParam(name = "files", required = false) List<MultipartFile> files) {
        if (errors.hasErrors()) return "posts/form";
        Long id = posts.create(form, files == null ? List.of() : files);
        return "redirect:/posts/" + id;
    }

    @GetMapping("/posts/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("post", posts.get(id));
        return "posts/detail";
    }

    @GetMapping("/posts/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        Post post = posts.get(id);
        PostForm form = new PostForm();
        form.setTitle(post.getTitle());
        form.setContent(post.getContent());
        model.addAttribute("postForm", form);
        model.addAttribute("postId", id);
        return "posts/form";
    }

    @PostMapping("/posts/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("postForm") PostForm form, BindingResult errors,
                         Model model) {
        if (errors.hasErrors()) {
            model.addAttribute("postId", id);
            return "posts/form";
        }
        posts.update(id, form);
        return "redirect:/posts/" + id;
    }

    @PostMapping("/posts/{id}/delete")
    public String delete(@PathVariable Long id) {
        posts.delete(id);
        return "redirect:/posts";
    }
}
